package fr.expand.project.importdata.audit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;

import fr.expand.project.importdata.dao.Neo4jDriverProvider;

import org.neo4j.driver.TransactionContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Append-only domain history, committed atomically with the corresponding mutation. */
public final class AuditTrail {
    private static final Gson JSON =
            new GsonBuilder()
                    .serializeNulls()
                    .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
                    .create();
    private static final int MAX_SNAPSHOT_BYTES = 1024 * 1024;

    private AuditTrail() {}

    public static void record(
            TransactionContext tx,
            String modelKey,
            AuditActor actor,
            String action,
            String entityType,
            String entityId,
            Object before,
            Object after) {
        Objects.requireNonNull(actor);
        if (modelKey == null || modelKey.isBlank())
            return; // legacy unscoped CLI data has no model history
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", UUID.randomUUID().toString());
        props.put("modelKey", modelKey);
        props.put("actor", actor.actor());
        props.put("effectiveUser", actor.effectiveUser());
        props.put("operationId", actor.operationId());
        props.put("action", action);
        props.put("entityType", entityType);
        props.put("entityId", entityId);
        props.put("beforeJson", snapshot(before));
        props.put("afterJson", snapshot(after));
        tx.run(
                        "CREATE (e:AuditData) SET e=$props, e.timestamp=toString(datetime())",
                        Map.of("props", props))
                .consume();
    }

    static String snapshot(Object value) {
        String json = JSON.toJson(value);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAX_SNAPSHOT_BYTES) return json;
        try {
            return JSON.toJson(
                    Map.of(
                            "snapshotOmitted",
                            true,
                            "reason",
                            "Snapshot exceeds 1 MiB",
                            "bytes",
                            bytes.length,
                            "sha256",
                            HexFormat.of()
                                    .formatHex(
                                            MessageDigest.getInstance("SHA-256").digest(bytes))));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static Map<String, Object> entity(long id, Map<String, Object> properties) {
        var result = new LinkedHashMap<String, Object>(properties);
        result.remove("searchText");
        result.put("id", id);
        return result;
    }

    public static Map<String, Object> object(TransactionContext tx, String key, long id) {
        var result =
                tx.run(
                        "MATCH (n:DataObject {modelKey:$key}) WHERE id(n)=$id RETURN properties(n)"
                                + " AS p",
                        Map.of("key", key, "id", id));
        return result.hasNext() ? entity(id, result.single().get("p").asMap()) : null;
    }

    public static Map<String, Object> link(TransactionContext tx, String key, long id) {
        var result =
                tx.run(
                        "MATCH (a:DataObject {modelKey:$key})-[r]->(b:DataObject {modelKey:$key})"
                                + " WHERE id(r)=$id RETURN properties(r) AS p, id(a) AS a, id(b) AS"
                                + " b,a.uuid AS au,b.uuid AS bu",
                        Map.of("key", key, "id", id));
        if (!result.hasNext()) return null;
        var row = result.single();
        var snapshot = entity(id, row.get("p").asMap());
        snapshot.put("fromId", row.get("a").asLong());
        snapshot.put("toId", row.get("b").asLong());
        snapshot.put("fromUuid", row.get("au").asObject());
        snapshot.put("toUuid", row.get("bu").asObject());
        return snapshot;
    }

    public static String identity(Map<String, Object> entity) {
        return String.valueOf(entity.getOrDefault("uuid", entity.get("id")));
    }

    /**
     * Capture cascading relationship deletion individually without building an unbounded snapshot.
     */
    public static void deletingLinks(
            TransactionContext tx, String key, long objectId, AuditActor actor) {
        var ids =
                tx.run(
                                "MATCH (n:DataObject {modelKey:$key})-[r]-(:DataObject"
                                    + " {modelKey:$key}) WHERE id(n)=$id RETURN DISTINCT id(r) AS"
                                    + " id",
                                Map.of("key", key, "id", objectId))
                        .list(r -> r.get("id").asLong());
        for (long id : ids) {
            var before = link(tx, key, id);
            record(tx, key, actor, "DELETE", "LINK", identity(before), before, null);
        }
    }

    public static Map<String, Object> list(
            String modelKey,
            int offset,
            int limit,
            String entityType,
            String entityId,
            String action) {
        if (modelKey == null || modelKey.isBlank() || modelKey.length() > 512)
            throw new IllegalArgumentException("Invalid model key");
        if (offset < 0 || limit < 1 || limit > 100)
            throw new IllegalArgumentException("offset must be nonnegative; limit must be 1..100");
        entityType = filter(entityType, Set.of("MODEL", "OBJECT", "LINK", "IMPORT"), "entityType");
        action = filter(action, Set.of("CREATE", "UPDATE", "DELETE", "IMPORT"), "action");
        if (entityId != null && entityId.length() > 512)
            throw new IllegalArgumentException("entityId too long");
        Map<String, Object> params = new HashMap<>();
        params.put("key", modelKey);
        params.put("offset", offset);
        params.put("limit", limit);
        params.put("type", entityType == null ? "" : entityType);
        params.put("entity", entityId == null ? "" : entityId);
        params.put("action", action == null ? "" : action);
        String match =
                "MATCH (e:AuditData {modelKey:$key}) WHERE ($type='' OR e.entityType=$type) AND"
                    + " ($entity='' OR e.entityId=$entity) AND ($action='' OR e.action=$action) ";
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            return session.executeRead(
                    tx -> {
                        long total =
                                tx.run(match + "RETURN count(e) AS total", params)
                                        .single()
                                        .get("total")
                                        .asLong();
                        var items =
                                tx.run(
                                                match
                                                        + "RETURN properties(e) AS p ORDER BY"
                                                        + " e.timestamp DESC,e.id DESC SKIP $offset"
                                                        + " LIMIT $limit",
                                                params)
                                        .list(
                                                record -> {
                                                    Map<String, Object> item =
                                                            new LinkedHashMap<>(
                                                                    record.get("p").asMap());
                                                    item.put(
                                                            "before",
                                                            JSON.fromJson(
                                                                    (String)
                                                                            item.remove(
                                                                                    "beforeJson"),
                                                                    Object.class));
                                                    item.put(
                                                            "after",
                                                            JSON.fromJson(
                                                                    (String)
                                                                            item.remove(
                                                                                    "afterJson"),
                                                                    Object.class));
                                                    return item;
                                                });
                        return Map.of(
                                "items",
                                items,
                                "total",
                                total,
                                "hasMore",
                                (long) offset + items.size() < total,
                                "offset",
                                offset,
                                "limit",
                                limit);
                    });
        }
    }

    private static String filter(String value, Set<String> allowed, String field) {
        if (value == null || value.isBlank()) return null;
        value = value.toUpperCase(Locale.ROOT);
        if (!allowed.contains(value)) throw new IllegalArgumentException("Invalid " + field);
        return value;
    }
}
