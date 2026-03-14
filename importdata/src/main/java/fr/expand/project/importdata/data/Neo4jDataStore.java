package fr.expand.project.importdata.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private static final String RELATION_ID_KEY = "relationId";
    private static final String RELATION_MODEL_KEY = "modelKey";
    private static final String RELATION_LINK_TYPE = "linkType";

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

                    Map<String, Object> row = new HashMap<>();
                    row.put("id", id);
                    row.put("type", type);
                row.put("attributes", toAttributeRows(props));
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
            ensureLinkIds(session, modelKey);
            return session.executeRead(tx -> {
                Map<String, Object> params = new HashMap<>();
                params.put("modelKey", modelKey);
                Result result = tx.run(
                    "MATCH (a:DataObject {modelKey:$modelKey})-[r]->(b:DataObject {modelKey:$modelKey}) "
                        + "RETURN id(a) AS fromId, id(b) AS toId, r.relationId AS relationId, "
                        + "r.linkType AS linkType, type(r) AS relType, properties(r) AS props "
                        + "ORDER BY id(a), id(b), r.relationId",
                    params
                );

                List<Map<String, Object>> links = new ArrayList<>();
                while (result.hasNext()) {
                    links.add(mapLinkRecord(result.next()));
                }
                return links;
            });
        }
    }

    public Map<String, Object> loadLinkByRelationId(String modelKey, String relationId) {
        if (modelKey == null || modelKey.isBlank() || relationId == null || relationId.isBlank()) {
            return null;
        }

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Map<String, Object> params = new HashMap<>();
                params.put("modelKey", modelKey);
                params.put("relationId", relationId);
                Result result = tx.run(
                    "MATCH (a:DataObject {modelKey:$modelKey})-[r {relationId:$relationId}]->(b:DataObject {modelKey:$modelKey}) "
                        + "RETURN id(a) AS fromId, id(b) AS toId, r.relationId AS relationId, "
                        + "r.linkType AS linkType, type(r) AS relType, properties(r) AS props "
                        + "LIMIT 1",
                    params
                );
                if (!result.hasNext()) {
                    return null;
                }
                return mapLinkRecord(result.next());
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

                Map<String, Object> row = new HashMap<>();
                row.put("id", id);
                row.put("type", type);
                row.put("attributes", toAttributeRows(props));
                return row;
            });
        }
    }

    public Map<String, Object> createLink(
        String modelKey,
        long fromId,
        long toId,
        String linkType,
        Map<String, Object> attributes
    ) {
        if (modelKey == null || modelKey.isBlank() || fromId <= 0 || toId <= 0 || linkType == null || linkType.isBlank()) {
            return null;
        }

        Map<String, Object> relationProperties = new LinkedHashMap<>();
        relationProperties.put(RELATION_MODEL_KEY, modelKey);
        relationProperties.put(RELATION_LINK_TYPE, linkType);
        relationProperties.put(RELATION_ID_KEY, UUID.randomUUID().toString());
        relationProperties.putAll(sanitizeRelationAttributes(attributes));

        try (Session session = driver.session()) {
            return session.executeWrite(tx -> {
                Map<String, Object> params = new HashMap<>();
                params.put("modelKey", modelKey);
                params.put("fromId", fromId);
                params.put("toId", toId);
                params.put("properties", relationProperties);
                Result result = tx.run(
                    "MATCH (a:DataObject {modelKey:$modelKey}) WHERE id(a)=$fromId "
                        + "MATCH (b:DataObject {modelKey:$modelKey}) WHERE id(b)=$toId "
                        + "CREATE (a)-[r:" + normalizeRelationshipType(linkType) + " $properties]->(b) "
                        + "RETURN id(a) AS fromId, id(b) AS toId, r.relationId AS relationId, "
                        + "r.linkType AS linkType, type(r) AS relType, properties(r) AS props",
                    params
                );
                if (!result.hasNext()) {
                    return null;
                }
                return mapLinkRecord(result.next());
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

    public boolean updateLink(String modelKey, String relationId, Map<String, Object> attributes) {
        if (modelKey == null || modelKey.isBlank() || relationId == null || relationId.isBlank()) {
            return false;
        }

        Map<String, Object> sanitizedAttributes = sanitizeRelationAttributes(attributes);
        try (Session session = driver.session()) {
            return session.executeWrite(tx -> {
                Map<String, Object> params = new HashMap<>();
                params.put("modelKey", modelKey);
                params.put("relationId", relationId);
                params.put("attributes", sanitizedAttributes);
                Result result = tx.run(
                    "MATCH (a:DataObject {modelKey:$modelKey})-[r {relationId:$relationId}]->(b:DataObject {modelKey:$modelKey}) "
                        + "WITH r, coalesce(r.linkType, type(r)) AS existingLinkType "
                        + "SET r = {modelKey:$modelKey, linkType:existingLinkType, relationId:$relationId} "
                        + "SET r += $attributes "
                        + "RETURN r.relationId AS relationId",
                    params
                );
                return result.hasNext();
            });
        }
    }

    public boolean deleteLink(String modelKey, String relationId) {
        if (modelKey == null || modelKey.isBlank() || relationId == null || relationId.isBlank()) {
            return false;
        }
        try (Session session = driver.session()) {
            return session.executeWrite(tx -> {
                Map<String, Object> params = new HashMap<>();
                params.put("modelKey", modelKey);
                params.put("relationId", relationId);
                Result result = tx.run(
                    "MATCH (a:DataObject {modelKey:$modelKey})-[r {relationId:$relationId}]->(b:DataObject {modelKey:$modelKey}) "
                        + "WITH r LIMIT 1 DELETE r RETURN 1 AS deleted",
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

    private void ensureLinkIds(Session session, String modelKey) {
        session.executeWrite(tx -> {
            Map<String, Object> params = new HashMap<>();
            params.put("modelKey", modelKey);
            tx.run(
                "MATCH (a:DataObject {modelKey:$modelKey})-[r]->(b:DataObject {modelKey:$modelKey}) "
                    + "WHERE r.relationId IS NULL "
                    + "SET r.relationId = randomUUID()",
                params
            );
            return null;
        });
    }

    private Map<String, Object> mapLinkRecord(Record record) {
        long fromId = record.get("fromId").asLong();
        long toId = record.get("toId").asLong();
        String relationId = record.get("relationId").isNull() ? "" : record.get("relationId").asString();
        String linkType = record.get("linkType").isNull() ? "" : record.get("linkType").asString();
        String relType = record.get("relType").isNull() ? "" : record.get("relType").asString();
        String type = linkType != null && !linkType.isBlank() ? linkType : relType;
        Map<String, Object> props = new HashMap<>(record.get("props").asMap());
        props.remove(RELATION_MODEL_KEY);
        props.remove(RELATION_LINK_TYPE);
        props.remove(RELATION_ID_KEY);

        Map<String, Object> row = new HashMap<>();
        row.put("id", relationId);
        row.put("fromId", fromId);
        row.put("toId", toId);
        row.put("type", type);
        row.put("relationshipType", relType);
        row.put("linkType", linkType);
        row.put("attributes", toAttributeRows(props));
        return row;
    }

    private Map<String, Object> sanitizeRelationAttributes(Map<String, Object> attributes) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        if (attributes == null) {
            return sanitized;
        }
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            if (RELATION_MODEL_KEY.equals(key) || RELATION_LINK_TYPE.equals(key) || RELATION_ID_KEY.equals(key)) {
                continue;
            }
            sanitized.put(key, entry.getValue() == null ? "" : entry.getValue().toString());
        }
        return sanitized;
    }

    private List<Map<String, Object>> toAttributeRows(Map<String, Object> props) {
        List<Map<String, Object>> attributes = new ArrayList<>();
        List<String> keys = new ArrayList<>(props.keySet());
        keys.sort(String::compareTo);
        for (String key : keys) {
            Map<String, Object> attribute = new HashMap<>();
            attribute.put("key", key);
            Object value = props.get(key);
            attribute.put("value", value == null ? "" : value.toString());
            attributes.add(attribute);
        }
        return attributes;
    }

    private String normalizeRelationshipType(String linkType) {
        if (linkType == null || linkType.isBlank()) {
            return "KNOWS";
        }
        String sanitized = linkType.trim().replaceAll("[^A-Za-z0-9_]", "_");
        if (sanitized.isBlank()) {
            return "KNOWS";
        }
        return sanitized.toUpperCase();
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
