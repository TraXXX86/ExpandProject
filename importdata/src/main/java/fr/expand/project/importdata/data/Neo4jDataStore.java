package fr.expand.project.importdata.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

                    Map<String, Object> props = record.get("props").asMap();
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
