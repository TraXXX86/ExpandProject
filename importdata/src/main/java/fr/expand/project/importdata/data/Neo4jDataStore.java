package fr.expand.project.importdata.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;

import fr.expand.project.importdata.config.Neo4jConfig;

public class Neo4jDataStore implements AutoCloseable {

    private final Neo4jConfig neo4jConfig;
    private final Driver driver;

    public Neo4jDataStore() {
        this(Neo4jConfig.fromSystem());
    }

    public Neo4jDataStore(Neo4jConfig neo4jConfig) {
        this.neo4jConfig = neo4jConfig == null ? Neo4jConfig.fromSystem() : neo4jConfig;
        this.driver = this.neo4jConfig.createDriver();
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

}
