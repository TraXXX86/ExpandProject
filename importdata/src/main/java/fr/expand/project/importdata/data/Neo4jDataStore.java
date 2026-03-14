package fr.expand.project.importdata.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.neo4j.driver.AuthToken;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;

public class Neo4jDataStore implements AutoCloseable {

    private static final String DEFAULT_BOLT_URI = "bolt://localhost:7687";
    private static final String DEFAULT_USER = "neo4j";
    private static final String DEFAULT_PASSWORD = "expand";
    private static final String OBJECT_PAGE_FILTER =
        "($typesEmpty OR ANY(label IN labels(n) WHERE label <> 'DataObject' AND label IN $types)) "
            + "AND ($query = '' "
            + "OR toLower(coalesce([label IN labels(n) WHERE label <> 'DataObject'][0], 'object')) CONTAINS $query "
            + "OR toString(id(n)) CONTAINS $query "
            + "OR EXISTS { "
            + "    UNWIND keys(properties(n)) AS key "
            + "    WITH n, key "
            + "    WHERE key <> 'modelKey' AND toLower(coalesce(toString(n[key]), '')) CONTAINS $query "
            + "    RETURN 1 "
            + "}) "
            + "AND ($hasAttributeFilter = false OR EXISTS { "
            + "    UNWIND keys(properties(n)) AS key "
            + "    WITH n, key "
            + "    WHERE key <> 'modelKey' "
            + "      AND ($attributeKey = '' "
            + "        OR ($attributeKeyExact AND toLower(key) = $attributeKey) "
            + "        OR ((NOT $attributeKeyExact) AND toLower(key) CONTAINS $attributeKey)) "
            + "      AND ($attributeValue = '' "
            + "        OR ($attributeValueExact AND toLower(coalesce(toString(n[key]), '')) = $attributeValue) "
            + "        OR ((NOT $attributeValueExact) "
            + "            AND toLower(coalesce(toString(n[key]), '')) CONTAINS $attributeValue)) "
            + "    RETURN 1 "
            + "})";
    private static final String SEARCH_FILTER =
        "($typesEmpty OR ANY(label IN labels(n) WHERE label <> 'DataObject' AND label IN $types)) "
            + "AND ($query <> '' AND EXISTS { "
            + "    UNWIND keys(properties(n)) AS key "
            + "    WITH n, key "
            + "    WHERE key <> 'modelKey' "
            + "      AND ($searchAllAttributes OR key IN $searchableAttributes) "
            + "      AND toLower(coalesce(toString(n[key]), '')) CONTAINS $query "
            + "    RETURN 1 "
            + "})";

    private final Driver driver;

    public record ObjectQueryOptions(
        int offset,
        int limit,
        List<String> types,
        String query,
        String attributeKey,
        String attributeKeyOperator,
        String attributeValue,
        String attributeValueOperator
    ) {
        public ObjectQueryOptions {
            offset = Math.max(offset, 0);
            limit = Math.max(limit, 1);
            types = sanitizeValues(types);
            query = normalizeText(query);
            attributeKey = normalizeText(attributeKey);
            attributeKeyOperator = normalizeOperator(attributeKeyOperator);
            attributeValue = normalizeText(attributeValue);
            attributeValueOperator = normalizeOperator(attributeValueOperator);
        }
    }

    public record SearchQueryOptions(
        int offset,
        int limit,
        List<String> types,
        String query,
        List<String> searchableAttributes
    ) {
        public SearchQueryOptions {
            offset = Math.max(offset, 0);
            limit = Math.max(limit, 1);
            types = sanitizeValues(types);
            query = normalizeText(query);
            searchableAttributes = sanitizeValues(searchableAttributes);
        }
    }

    public record PageResult(
        int totalCount,
        int offset,
        int limit,
        List<Map<String, Object>> items
    ) {
    }

    public record DataSummary(
        int objectCount,
        int linkCount,
        List<String> objectTypes
    ) {
    }

    public record NeighborResult(
        Map<String, Object> object,
        List<Map<String, Object>> neighbors,
        List<Map<String, Object>> links
    ) {
    }

    public Neo4jDataStore() {
        String uri = readSetting("NEO4J_BOLT_URI", "NEO4J_URI", DEFAULT_BOLT_URI);
        AuthToken authToken = buildAuthToken();
        this.driver = GraphDatabase.driver(uri, authToken);
    }

    public DataSummary loadDataSummary(String modelKey) {
        if (modelKey == null || modelKey.isBlank()) {
            return new DataSummary(0, 0, List.of());
        }

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Map<String, Object> params = new HashMap<>();
                params.put("modelKey", modelKey);

                long objectCount = tx.run(
                    "MATCH (n:DataObject {modelKey:$modelKey}) RETURN count(n) AS count",
                    params
                ).single().get("count").asLong();

                long linkCount = tx.run(
                    "MATCH (:DataObject {modelKey:$modelKey})-[r]->(:DataObject {modelKey:$modelKey}) "
                        + "RETURN count(r) AS count",
                    params
                ).single().get("count").asLong();

                Result typeResult = tx.run(
                    "MATCH (n:DataObject {modelKey:$modelKey}) "
                        + "WITH [label IN labels(n) WHERE label <> 'DataObject'][0] AS type "
                        + "WHERE type IS NOT NULL AND type <> '' "
                        + "RETURN DISTINCT type ORDER BY type",
                    params
                );

                List<String> objectTypes = new ArrayList<>();
                while (typeResult.hasNext()) {
                    String type = typeResult.next().get("type").asString("");
                    if (!type.isBlank()) {
                        objectTypes.add(type);
                    }
                }

                return new DataSummary(toInt(objectCount), toInt(linkCount), objectTypes);
            });
        }
    }

    public PageResult loadObjectsPage(String modelKey, ObjectQueryOptions options) {
        if (modelKey == null || modelKey.isBlank()) {
            return new PageResult(0, 0, 0, List.of());
        }
        ObjectQueryOptions safeOptions = options == null
            ? new ObjectQueryOptions(0, 25, List.of(), "", "", "contains", "", "contains")
            : options;

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Map<String, Object> params = buildObjectPageParams(modelKey, safeOptions);

                int totalCount = toInt(tx.run(
                    "MATCH (n:DataObject {modelKey:$modelKey}) WHERE " + OBJECT_PAGE_FILTER
                        + " RETURN count(n) AS count",
                    params
                ).single().get("count").asLong());

                Result result = tx.run(
                    "MATCH (n:DataObject {modelKey:$modelKey}) WHERE " + OBJECT_PAGE_FILTER
                        + " RETURN id(n) AS id, labels(n) AS labels, properties(n) AS props "
                        + "ORDER BY id(n) SKIP $offset LIMIT $limit",
                    params
                );

                List<Map<String, Object>> items = new ArrayList<>();
                while (result.hasNext()) {
                    items.add(mapObjectRecord(result.next(), "id", "labels", "props"));
                }

                return new PageResult(totalCount, safeOptions.offset(), safeOptions.limit(), items);
            });
        }
    }

    public PageResult searchObjects(String modelKey, SearchQueryOptions options) {
        if (modelKey == null || modelKey.isBlank()) {
            return new PageResult(0, 0, 0, List.of());
        }
        SearchQueryOptions safeOptions = options == null
            ? new SearchQueryOptions(0, 25, List.of(), "", List.of())
            : options;
        if (safeOptions.query().isBlank()) {
            return new PageResult(0, safeOptions.offset(), safeOptions.limit(), List.of());
        }

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Map<String, Object> params = buildSearchParams(modelKey, safeOptions);

                int totalCount = toInt(tx.run(
                    "MATCH (n:DataObject {modelKey:$modelKey}) WHERE " + SEARCH_FILTER
                        + " RETURN count(n) AS count",
                    params
                ).single().get("count").asLong());

                Result result = tx.run(
                    "MATCH (n:DataObject {modelKey:$modelKey}) WHERE " + SEARCH_FILTER
                        + " RETURN id(n) AS id, labels(n) AS labels, properties(n) AS props "
                        + "ORDER BY id(n) SKIP $offset LIMIT $limit",
                    params
                );

                List<Map<String, Object>> items = new ArrayList<>();
                while (result.hasNext()) {
                    Map<String, Object> row = mapObjectRecord(result.next(), "id", "labels", "props");
                    row.put("matches", buildMatches(row, safeOptions.query(), safeOptions.searchableAttributes()));
                    items.add(row);
                }

                return new PageResult(totalCount, safeOptions.offset(), safeOptions.limit(), items);
            });
        }
    }

    public NeighborResult loadNeighbors(String modelKey, long objectId) {
        if (modelKey == null || modelKey.isBlank() || objectId <= 0) {
            return new NeighborResult(null, List.of(), List.of());
        }

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Map<String, Object> center = loadObjectById(tx, modelKey, objectId);
                if (center == null) {
                    return new NeighborResult(null, List.of(), List.of());
                }

                Map<String, Object> params = new HashMap<>();
                params.put("modelKey", modelKey);
                params.put("objectId", objectId);

                Result result = tx.run(
                    "MATCH (n:DataObject {modelKey:$modelKey}) WHERE id(n)=$objectId "
                        + "MATCH (n)-[r]-(m:DataObject {modelKey:$modelKey}) "
                        + "RETURN id(m) AS neighborId, labels(m) AS neighborLabels, properties(m) AS neighborProps, "
                        + "id(startNode(r)) AS fromId, id(endNode(r)) AS toId, "
                        + "r.linkType AS linkType, type(r) AS relType "
                        + "ORDER BY id(m), id(startNode(r)), id(endNode(r)), type(r)",
                    params
                );

                Map<String, Map<String, Object>> neighbors = new LinkedHashMap<>();
                List<Map<String, Object>> links = new ArrayList<>();
                while (result.hasNext()) {
                    Record record = result.next();
                    Map<String, Object> neighbor = mapObjectRecord(
                        record,
                        "neighborId",
                        "neighborLabels",
                        "neighborProps"
                    );
                    Object neighborId = neighbor.get("id");
                    if (neighborId != null) {
                        neighbors.putIfAbsent(neighborId.toString(), neighbor);
                    }
                    links.add(mapLinkRecord(record));
                }

                return new NeighborResult(center, new ArrayList<>(neighbors.values()), links);
            });
        }
    }

    public List<Map<String, Object>> loadObjects(String modelKey) {
        if (modelKey == null || modelKey.isBlank()) {
            return List.of();
        }

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Map<String, Object> params = new HashMap<>();
                params.put("modelKey", modelKey);
                Result result = tx.run(
                    "MATCH (n:DataObject {modelKey:$modelKey}) "
                        + "RETURN id(n) AS id, labels(n) AS labels, properties(n) AS props "
                        + "ORDER BY id(n)",
                    params
                );

                List<Map<String, Object>> objects = new ArrayList<>();
                while (result.hasNext()) {
                    Record record = result.next();
                    long id = record.get("id").asLong();
                    List<Object> labels = record.get("labels").asList();
                    String type = resolveType(labels);

                    Map<String, Object> props = new HashMap<>(record.get("props").asMap());
                    props.remove("modelKey");

                    List<Map<String, Object>> attributes = new ArrayList<>();
                    for (Map.Entry<String, Object> entry : props.entrySet()) {
                        Map<String, Object> attribute = new HashMap<>();
                        attribute.put("key", entry.getKey());
                        attribute.put("value", entry.getValue() == null ? "" : entry.getValue().toString());
                        attributes.add(attribute);
                    }

                    Map<String, Object> row = new HashMap<>();
                    row.put("id", id);
                    row.put("type", type);
                    row.put("attributes", attributes);
                    objects.add(row);
                }
                return objects;
            });
        }
    }

    public List<Map<String, Object>> loadLinks(String modelKey) {
        if (modelKey == null || modelKey.isBlank()) {
            return List.of();
        }

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Map<String, Object> params = new HashMap<>();
                params.put("modelKey", modelKey);
                Result result = tx.run(
                    "MATCH (a:DataObject {modelKey:$modelKey})-[r]->(b:DataObject {modelKey:$modelKey}) "
                        + "RETURN id(a) AS fromId, id(b) AS toId, r.linkType AS linkType, type(r) AS relType "
                        + "ORDER BY id(a), id(b)",
                    params
                );

                List<Map<String, Object>> links = new ArrayList<>();
                while (result.hasNext()) {
                    Record record = result.next();
                    long fromId = record.get("fromId").asLong();
                    long toId = record.get("toId").asLong();
                    String linkType = record.get("linkType").isNull() ? "" : record.get("linkType").asString();
                    String relType = record.get("relType").isNull() ? "" : record.get("relType").asString();
                    String type = linkType != null && !linkType.isBlank() ? linkType : relType;

                    Map<String, Object> row = new HashMap<>();
                    row.put("fromId", fromId);
                    row.put("toId", toId);
                    row.put("type", type);
                    row.put("relationshipType", relType);
                    row.put("linkType", linkType);
                    links.add(row);
                }
                return links;
            });
        }
    }

    public Map<String, Object> loadObjectById(String modelKey, long objectId) {
        if (modelKey == null || modelKey.isBlank() || objectId <= 0) {
            return null;
        }

        try (Session session = driver.session()) {
            return session.executeRead(tx -> loadObjectById(tx, modelKey, objectId));
        }
    }

    public boolean updateObject(String modelKey, long objectId, Map<String, Object> attributes) {
        if (modelKey == null || modelKey.isBlank() || objectId <= 0) {
            return false;
        }
        if (attributes == null || attributes.isEmpty()) {
            return true;
        }

        Map<String, Object> sanitizedAttributes = new HashMap<>();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank() || "modelKey".equals(key)) {
                continue;
            }
            sanitizedAttributes.put(key, entry.getValue() == null ? "" : entry.getValue().toString());
        }

        try (Session session = driver.session()) {
            return session.executeWrite(tx -> {
                Map<String, Object> params = new HashMap<>();
                params.put("modelKey", modelKey);
                params.put("objectId", objectId);
                params.put("attributes", sanitizedAttributes);
                var result = tx.run(
                    "MATCH (n:DataObject {modelKey:$modelKey}) "
                        + "WHERE id(n)=$objectId "
                        + "SET n += $attributes "
                        + "RETURN id(n) AS id",
                    params
                );
                return result.hasNext();
            });
        }
    }

    public boolean deleteObject(String modelKey, long objectId) {
        if (modelKey == null || modelKey.isBlank() || objectId <= 0) {
            return false;
        }
        try (Session session = driver.session()) {
            return session.executeWrite(tx -> {
                Map<String, Object> params = new HashMap<>();
                params.put("modelKey", modelKey);
                params.put("objectId", objectId);
                var result = tx.run(
                    "MATCH (n:DataObject {modelKey:$modelKey}) "
                        + "WHERE id(n)=$objectId "
                        + "WITH n LIMIT 1 "
                        + "DETACH DELETE n "
                        + "RETURN 1 AS deleted",
                    params
                );
                return result.hasNext();
            });
        }
    }

    @Override
    public void close() {
        if (driver != null) {
            driver.close();
        }
    }

    private String resolveType(List<Object> labels) {
        if (labels == null || labels.isEmpty()) {
            return "Object";
        }
        for (Object label : labels) {
            if (label == null) {
                continue;
            }
            String value = label.toString();
            if (!"DataObject".equals(value)) {
                return value;
            }
        }
        return "Object";
    }

    private Map<String, Object> loadObjectById(org.neo4j.driver.TransactionContext tx, String modelKey, long objectId) {
        Map<String, Object> params = new HashMap<>();
        params.put("modelKey", modelKey);
        params.put("objectId", objectId);
        Result result = tx.run(
            "MATCH (n:DataObject {modelKey:$modelKey}) "
                + "WHERE id(n)=$objectId "
                + "RETURN id(n) AS id, labels(n) AS labels, properties(n) AS props",
            params
        );
        if (!result.hasNext()) {
            return null;
        }
        return mapObjectRecord(result.next(), "id", "labels", "props");
    }

    private Map<String, Object> buildObjectPageParams(String modelKey, ObjectQueryOptions options) {
        Map<String, Object> params = new HashMap<>();
        params.put("modelKey", modelKey);
        params.put("offset", options.offset());
        params.put("limit", options.limit());
        params.put("types", options.types());
        params.put("typesEmpty", options.types().isEmpty());
        params.put("query", options.query());
        params.put("attributeKey", options.attributeKey());
        params.put("attributeKeyExact", "equals".equals(options.attributeKeyOperator()));
        params.put("attributeValue", options.attributeValue());
        params.put("attributeValueExact", "equals".equals(options.attributeValueOperator()));
        params.put(
            "hasAttributeFilter",
            !options.attributeKey().isBlank() || !options.attributeValue().isBlank()
        );
        return params;
    }

    private Map<String, Object> buildSearchParams(String modelKey, SearchQueryOptions options) {
        Map<String, Object> params = new HashMap<>();
        params.put("modelKey", modelKey);
        params.put("offset", options.offset());
        params.put("limit", options.limit());
        params.put("types", options.types());
        params.put("typesEmpty", options.types().isEmpty());
        params.put("query", options.query());
        params.put("searchableAttributes", options.searchableAttributes());
        params.put("searchAllAttributes", options.searchableAttributes().isEmpty());
        return params;
    }

    private Map<String, Object> mapObjectRecord(Record record, String idField, String labelsField, String propsField) {
        long id = record.get(idField).asLong();
        List<Object> labels = record.get(labelsField).asList();
        String type = resolveType(labels);

        Map<String, Object> props = new HashMap<>(record.get(propsField).asMap());
        props.remove("modelKey");

        List<Map<String, Object>> attributes = new ArrayList<>();
        List<Map.Entry<String, Object>> entries = new ArrayList<>(props.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        for (Map.Entry<String, Object> entry : entries) {
            Map<String, Object> attribute = new HashMap<>();
            attribute.put("key", entry.getKey());
            attribute.put("value", entry.getValue() == null ? "" : entry.getValue().toString());
            attributes.add(attribute);
        }

        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("type", type);
        row.put("attributes", attributes);
        return row;
    }

    private Map<String, Object> mapLinkRecord(Record record) {
        long fromId = record.get("fromId").asLong();
        long toId = record.get("toId").asLong();
        String linkType = record.get("linkType").isNull() ? "" : record.get("linkType").asString();
        String relType = record.get("relType").isNull() ? "" : record.get("relType").asString();
        String type = linkType != null && !linkType.isBlank() ? linkType : relType;

        Map<String, Object> row = new HashMap<>();
        row.put("fromId", fromId);
        row.put("toId", toId);
        row.put("type", type);
        row.put("relationshipType", relType);
        row.put("linkType", linkType);
        return row;
    }

    private List<Map<String, Object>> buildMatches(
        Map<String, Object> object,
        String query,
        List<String> searchableAttributes
    ) {
        String normalizedQuery = normalizeText(query);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }
        Set<String> allowedAttributes = new LinkedHashSet<>(sanitizeValues(searchableAttributes));
        boolean searchAllAttributes = allowedAttributes.isEmpty();

        List<Map<String, Object>> matches = new ArrayList<>();
        Object attributesNode = object.get("attributes");
        if (!(attributesNode instanceof List<?> attributes)) {
            return matches;
        }
        for (Object attributeNode : attributes) {
            if (!(attributeNode instanceof Map<?, ?> attributeMap)) {
                continue;
            }
            Object keyValue = attributeMap.get("key");
            if (keyValue == null) {
                continue;
            }
            String key = keyValue.toString();
            if (!searchAllAttributes && !allowedAttributes.contains(key)) {
                continue;
            }
            String value = attributeMap.get("value") == null ? "" : attributeMap.get("value").toString();
            if (!value.toLowerCase().contains(normalizedQuery)) {
                continue;
            }
            Map<String, Object> match = new HashMap<>();
            match.put("key", key);
            match.put("value", value);
            matches.add(match);
        }
        return matches;
    }

    private static int toInt(long value) {
        if (value <= 0L) {
            return 0;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }

    private static List<String> sanitizeValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> sanitized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String normalized = value.trim();
            if (!normalized.isBlank()) {
                sanitized.add(normalized);
            }
        }
        return new ArrayList<>(sanitized);
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String normalizeOperator(String value) {
        return "equals".equalsIgnoreCase(value == null ? "" : value.trim()) ? "equals" : "contains";
    }

    private AuthToken buildAuthToken() {
        String auth = readSetting("NEO4J_AUTH", null, null);
        if (auth != null && !auth.isBlank()) {
            if ("none".equalsIgnoreCase(auth.trim())) {
                return AuthTokens.none();
            }
            int separatorIndex = auth.indexOf('/');
            if (separatorIndex > 0 && separatorIndex < auth.length() - 1) {
                String user = auth.substring(0, separatorIndex);
                String password = auth.substring(separatorIndex + 1);
                return AuthTokens.basic(user, password);
            }
        }

        String user = readSetting("NEO4J_USER", null, DEFAULT_USER);
        String password = readSetting("NEO4J_PASSWORD", null, DEFAULT_PASSWORD);
        return AuthTokens.basic(user, password);
    }

    private String readSetting(String envKey, String fallbackEnvKey, String defaultValue) {
        String value = System.getProperty(envKey);
        if (value == null || value.isBlank()) {
            value = System.getenv(envKey);
        }
        if ((value == null || value.isBlank()) && fallbackEnvKey != null) {
            value = System.getProperty(fallbackEnvKey);
            if (value == null || value.isBlank()) {
                value = System.getenv(fallbackEnvKey);
            }
        }
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
