package fr.expand.project.importdata.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

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

    private final Driver driver;

    public Neo4jDataStore() {
        String uri = readSetting("NEO4J_BOLT_URI", "NEO4J_URI", DEFAULT_BOLT_URI);
        AuthToken authToken = buildAuthToken();
        this.driver = GraphDatabase.driver(uri, authToken);
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
            return session.executeRead(tx -> {
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
                return row;
            });
        }
    }

    public List<Map<String, Object>> searchObjects(
        String modelKey,
        String query,
        List<String> typeFilters,
        Map<String, List<String>> searchableAttributesByType,
        Map<String, List<String>> representativeAttributesByType,
        int limit
    ) {
        if (modelKey == null || modelKey.isBlank() || query == null || query.isBlank()) {
            return List.of();
        }
        if (searchableAttributesByType == null || searchableAttributesByType.isEmpty()) {
            return List.of();
        }

        List<String> requestedTypes = typeFilters == null ? List.of() : typeFilters;
        int safeLimit = limit <= 0 ? 100 : limit;

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Map<String, Object> params = new HashMap<>();
                params.put("modelKey", modelKey);
                params.put("query", query.toLowerCase(Locale.ROOT));
                params.put("typeFilters", requestedTypes);
                params.put("searchableAttributesByType", searchableAttributesByType);
                params.put("limit", safeLimit);

                Result result = tx.run(
                    "WITH $searchableAttributesByType AS searchableAttributesByType, "
                        + "$typeFilters AS typeFilters, "
                        + "$query AS searchQuery "
                        + "MATCH (n:DataObject {modelKey:$modelKey}) "
                        + "WITH n, searchQuery, searchableAttributesByType, typeFilters, "
                        + "[label IN labels(n) WHERE label <> 'DataObject'][0] AS objectType "
                        + "WHERE (size(typeFilters) = 0 OR objectType IN typeFilters) "
                        + "AND any(attributeName IN coalesce(searchableAttributesByType[objectType], []) "
                        + "WHERE toLower(toString(coalesce(n[attributeName], ''))) CONTAINS searchQuery) "
                        + "RETURN id(n) AS id, objectType AS type, properties(n) AS props "
                        + "ORDER BY id(n) "
                        + "LIMIT $limit",
                    params
                );

                List<Map<String, Object>> results = new ArrayList<>();
                while (result.hasNext()) {
                    Record record = result.next();
                    long id = record.get("id").asLong();
                    String type = record.get("type").isNull() ? "Object" : record.get("type").asString();
                    Map<String, Object> props = new HashMap<>(record.get("props").asMap());
                    props.remove("modelKey");

                    List<Map<String, Object>> matches = extractMatches(
                        type,
                        props,
                        query,
                        searchableAttributesByType
                    );
                    if (matches.isEmpty()) {
                        continue;
                    }

                    List<Map<String, Object>> primaryAttributes = extractAttributes(
                        type,
                        props,
                        representativeAttributesByType
                    );

                    Map<String, Object> row = new HashMap<>();
                    row.put("id", id);
                    row.put("type", type);
                    row.put("matches", matches);
                    row.put("primaryAttributes", primaryAttributes);
                    results.add(row);
                }
                return results;
            });
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

    private List<Map<String, Object>> extractMatches(
        String type,
        Map<String, Object> props,
        String query,
        Map<String, List<String>> searchableAttributesByType
    ) {
        List<Map<String, Object>> matches = new ArrayList<>();
        if (props == null || props.isEmpty() || searchableAttributesByType == null) {
            return matches;
        }
        List<String> searchableAttributes = searchableAttributesByType.get(type);
        if (searchableAttributes == null || searchableAttributes.isEmpty()) {
            return matches;
        }

        String normalizedQuery = query == null ? "" : query.toLowerCase(Locale.ROOT);
        for (String attributeName : searchableAttributes) {
            if (attributeName == null || attributeName.isBlank() || !props.containsKey(attributeName)) {
                continue;
            }
            String value = props.get(attributeName) == null ? "" : props.get(attributeName).toString();
            if (!value.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                continue;
            }
            Map<String, Object> attribute = new HashMap<>();
            attribute.put("key", attributeName);
            attribute.put("value", value);
            matches.add(attribute);
        }
        return matches;
    }

    private List<Map<String, Object>> extractAttributes(
        String type,
        Map<String, Object> props,
        Map<String, List<String>> attributesByType
    ) {
        List<Map<String, Object>> attributes = new ArrayList<>();
        if (props == null || props.isEmpty() || attributesByType == null) {
            return attributes;
        }
        List<String> attributeNames = attributesByType.get(type);
        if (attributeNames == null || attributeNames.isEmpty()) {
            return attributes;
        }

        for (String attributeName : attributeNames) {
            if (attributeName == null || attributeName.isBlank() || !props.containsKey(attributeName)) {
                continue;
            }
            String value = props.get(attributeName) == null ? "" : props.get(attributeName).toString();
            if (value.isBlank()) {
                continue;
            }
            Map<String, Object> attribute = new HashMap<>();
            attribute.put("key", attributeName);
            attribute.put("value", value);
            attributes.add(attribute);
        }
        return attributes;
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
