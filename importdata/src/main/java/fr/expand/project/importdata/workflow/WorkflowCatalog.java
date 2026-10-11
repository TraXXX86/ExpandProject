package fr.expand.project.importdata.workflow;

import com.google.gson.Gson;

import fr.expand.project.importdata.audit.*;
import fr.expand.project.importdata.dao.*;
import fr.expand.project.importdata.model.ModelManager;
import fr.expand.project.importdata.model.generated.DATAMODEL;

import org.neo4j.driver.TransactionContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Immutable per-model definitions and explicit, serialized activation for new objects. */
public final class WorkflowCatalog {
    private static final Gson JSON = new Gson();

    public static String lock(TransactionContext tx, String key) {
        var result =
                tx.run(
                        "MATCH (m:DataModel {key:$key}) SET m.key=m.key RETURN m.xml AS xml",
                        Map.of("key", key));
        if (!result.hasNext()) throw new IllegalArgumentException("Model not found");
        return result.single().get("xml").asString();
    }

    public static ModelManager model(TransactionContext tx, String key) {
        var result =
                tx.run("MATCH (m:DataModel {key:$key}) RETURN m.xml AS xml", Map.of("key", key));
        if (!result.hasNext()) throw new IllegalArgumentException("Model not found");
        try {
            var manager = new ModelManager();
            manager.loadModelFromXml(result.single().get("xml").asString());
            return manager;
        } catch (jakarta.xml.bind.JAXBException e) {
            throw new IllegalArgumentException("Invalid model", e);
        }
    }

    public static WorkflowDefinition load(
            TransactionContext tx, String key, String id, String version) {
        var rows =
                tx.run(
                                "MATCH (w:WorkflowDefinition"
                                    + " {modelKey:$key,id:$id,version:$version}) RETURN w.xml AS"
                                    + " xml",
                                Map.of("key", key, "id", id, "version", version))
                        .list();
        if (rows.size() != 1)
            throw new IllegalArgumentException("Workflow version not found or ambiguous");
        return WorkflowXml.parse(rows.get(0).get("xml").asString());
    }

    public static List<String> expandTypes(WorkflowDefinition definition, ModelManager manager) {
        var result = new TreeSet<String>();
        for (var ref : definition.objectTypes()) {
            if (!manager.objectTypeExists(ref.name()))
                throw new IllegalArgumentException("Unknown workflow object type: " + ref.name());
            result.add(ref.name());
            if (ref.includeSubtypes())
                for (var type : manager.getCurrentModel().getOBJECTTYPES().getOBJECTTYPE())
                    if (manager.isTypeOrSubtype(type.getNAME(), ref.name()))
                        result.add(type.getNAME());
        }
        return List.copyOf(result);
    }

    public WorkflowDefinition upload(String key, String xml, AuditActor actor) {
        var definition = WorkflowXml.parse(xml);
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            return session.executeWrite(
                    tx -> {
                        lock(tx, key);
                        expandTypes(definition, model(tx, key));
                        var params =
                                Map.<String, Object>of(
                                        "key",
                                        key,
                                        "id",
                                        definition.id(),
                                        "version",
                                        definition.version(),
                                        "xml",
                                        xml);
                        if (tx.run(
                                        "MATCH (w:WorkflowDefinition"
                                            + " {modelKey:$key,id:$id,version:$version}) RETURN w",
                                        params)
                                .hasNext())
                            throw new StorageConflictException(
                                    "This workflow version already exists and is immutable; upload"
                                            + " a new version");
                        tx.run(
                                        "CREATE (w:WorkflowDefinition"
                                            + " {modelKey:$key,id:$id,version:$version,xml:$xml})",
                                        params)
                                .consume();
                        AuditTrail.record(
                                tx,
                                key,
                                actor,
                                "CREATE",
                                "WORKFLOW",
                                definition.id() + "@" + definition.version(),
                                null,
                                definition);
                        return definition;
                    });
        }
    }

    public static List<Map<String, Object>> bindings(TransactionContext tx, String key) {
        return tx.run(
                        "MATCH (b:WorkflowBinding {modelKey:$key}) RETURN b.objectType AS"
                                + " objectType,b.workflowId AS workflowId,b.workflowVersion AS"
                                + " workflowVersion ORDER BY objectType",
                        Map.of("key", key))
                .list(r -> r.asMap());
    }

    public Map<String, Object> list(String key) {
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            return session.executeRead(
                    tx -> {
                        model(tx, key);
                        var items =
                                tx.run(
                                                "MATCH (w:WorkflowDefinition {modelKey:$key})"
                                                        + " RETURN w.xml AS xml ORDER BY"
                                                        + " w.id,w.version",
                                                Map.of("key", key))
                                        .list(r -> WorkflowXml.parse(r.get("xml").asString()));
                        return Map.of("items", items, "bindings", bindings(tx, key));
                    });
        }
    }

    public Map<String, Object> activate(
            String key,
            String id,
            String version,
            String previewHash,
            boolean commit,
            AuditActor actor) {
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            org.neo4j.driver.TransactionCallback<Map<String, Object>> work =
                    tx -> {
                        String xml = commit ? lock(tx, key) : model(tx, key).getCurrentModelXml();
                        var definition = load(tx, key, id, version);
                        var types = expandTypes(definition, model(tx, key));
                        var previous = bindings(tx, key);
                        long existing =
                                tx.run(
                                                "MATCH (n:DataObject {modelKey:$key}) WHERE n.type"
                                                        + " IN $types RETURN count(n) AS count",
                                                Map.of("key", key, "types", types))
                                        .single()
                                        .get("count")
                                        .asLong();
                        var review = new LinkedHashMap<String, Object>();
                        review.put("id", id);
                        review.put("version", version);
                        review.put("objectTypes", types);
                        review.put("bindings", previous);
                        review.put("existingObjects", existing);
                        review.put("affectsExistingObjects", false);
                        String hash = hash(List.of(key, xml, fingerprint(tx, key), review));
                        review.put("previewHash", hash);
                        if (commit) {
                            requireHash(hash, previewHash);
                            for (String type : types)
                                tx.run(
                                                "MERGE (b:WorkflowBinding"
                                                    + " {modelKey:$key,objectType:$type}) SET"
                                                    + " b.workflowId=$id,b.workflowVersion=$version",
                                                Map.of(
                                                        "key", key, "type", type, "id", id,
                                                        "version", version))
                                        .consume();
                            AuditTrail.record(
                                    tx,
                                    key,
                                    actor,
                                    "ACTIVATE",
                                    "WORKFLOW",
                                    id + "@" + version,
                                    previous,
                                    bindings(tx, key));
                        }
                        return review;
                    };
            return commit ? session.executeWrite(work) : session.executeRead(work);
        }
    }

    public void deactivate(String key, List<String> types, AuditActor actor) {
        if (types == null || types.isEmpty() || types.size() > 1000)
            throw new IllegalArgumentException("Select 1..1000 object types");
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            session.executeWriteWithoutResult(
                    tx -> {
                        lock(tx, key);
                        var before = bindings(tx, key);
                        tx.run(
                                        "MATCH (b:WorkflowBinding {modelKey:$key}) WHERE"
                                                + " b.objectType IN $types DELETE b",
                                        Map.of("key", key, "types", types))
                                .consume();
                        AuditTrail.record(
                                tx,
                                key,
                                actor,
                                "DEACTIVATE",
                                "WORKFLOW",
                                "bindings",
                                before,
                                bindings(tx, key));
                    });
        }
    }

    public void delete(String key, String id, String version, AuditActor actor) {
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            session.executeWriteWithoutResult(
                    tx -> {
                        lock(tx, key);
                        var definition = load(tx, key, id, version);
                        var params =
                                Map.<String, Object>of("key", key, "id", id, "version", version);
                        if (tx.run(
                                                "MATCH (b:WorkflowBinding"
                                                    + " {modelKey:$key,workflowId:$id,workflowVersion:$version})"
                                                    + " RETURN b LIMIT 1",
                                                params)
                                        .hasNext()
                                || tx.run(
                                                "MATCH (n:DataObject"
                                                    + " {modelKey:$key,_workflowId:$id,_workflowVersion:$version})"
                                                    + " RETURN n LIMIT 1",
                                                params)
                                        .hasNext())
                            throw new StorageConflictException(
                                    "Cannot delete an active or used workflow version");
                        tx.run(
                                        "MATCH (w:WorkflowDefinition"
                                            + " {modelKey:$key,id:$id,version:$version}) DELETE w",
                                        params)
                                .consume();
                        AuditTrail.record(
                                tx,
                                key,
                                actor,
                                "DELETE",
                                "WORKFLOW",
                                id + "@" + version,
                                definition,
                                null);
                    });
        }
    }

    public static String fingerprint(TransactionContext tx, String key) {
        var definitions =
                tx.run(
                                "MATCH (w:WorkflowDefinition {modelKey:$key}) RETURN w.id AS"
                                    + " id,w.version AS version,w.xml AS xml ORDER BY id,version",
                                Map.of("key", key))
                        .list(r -> r.asMap());
        return hash(List.of(definitions, bindings(tx, key)));
    }

    public static void validateModelUpdate(TransactionContext tx, String key, DATAMODEL candidate) {
        var names = new HashSet<String>();
        for (var t : candidate.getOBJECTTYPES().getOBJECTTYPE()) names.add(t.getNAME());
        var definitions =
                tx.run(
                                "MATCH (w:WorkflowDefinition {modelKey:$key}) RETURN w.xml AS xml",
                                Map.of("key", key))
                        .list(r -> WorkflowXml.parse(r.get("xml").asString()));
        var allowedByVersion = new HashMap<String, Set<String>>();
        for (var d : definitions) {
            var allowed = new HashSet<String>();
            for (var ref : d.objectTypes()) {
                if (!names.contains(ref.name()))
                    throw new StorageConflictException(
                            "Type is referenced by workflow " + d.id() + ": " + ref.name());
                allowed.add(ref.name());
                if (ref.includeSubtypes())
                    for (var t : candidate.getOBJECTTYPES().getOBJECTTYPE()) {
                        String current = t.getNAME();
                        var seen = new HashSet<String>();
                        while (current != null && seen.add(current)) {
                            if (current.equals(ref.name())) {
                                allowed.add(t.getNAME());
                                break;
                            }
                            String parent = null;
                            for (var possible : candidate.getOBJECTTYPES().getOBJECTTYPE())
                                if (possible.getNAME().equals(current)) {
                                    parent = possible.getPARENT();
                                    break;
                                }
                            current = parent;
                        }
                    }
            }
            allowedByVersion.put(d.id() + "@" + d.version(), allowed);
        }
        for (var b : bindings(tx, key))
            if (!allowedByVersion
                    .getOrDefault(b.get("workflowId") + "@" + b.get("workflowVersion"), Set.of())
                    .contains(b.get("objectType")))
                throw new StorageConflictException(
                        "Model edit invalidates active workflow type: " + b.get("objectType"));
        var pinned =
                tx.run(
                                "MATCH (n:DataObject {modelKey:$key}) WHERE n._workflowId IS NOT"
                                        + " NULL RETURN DISTINCT n.type AS type,n._workflowId AS"
                                        + " workflowId,n._workflowVersion AS workflowVersion",
                                Map.of("key", key))
                        .list(r -> r.asMap());
        for (var p : pinned)
            if (!allowedByVersion
                    .getOrDefault(p.get("workflowId") + "@" + p.get("workflowVersion"), Set.of())
                    .contains(p.get("type")))
                throw new StorageConflictException(
                        "Model edit invalidates existing object workflow type: " + p.get("type"));
    }

    public static String hash(Object value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(JSON.toJson(value).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void requireHash(String actual, String expected) {
        if (expected == null
                || !MessageDigest.isEqual(
                        actual.getBytes(StandardCharsets.UTF_8),
                        expected.getBytes(StandardCharsets.UTF_8)))
            throw new StorageConflictException(
                    "Preview is missing or stale; preview again before committing");
    }
}
