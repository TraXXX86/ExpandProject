package fr.expand.project.importdata.workflow;

import fr.expand.project.importdata.audit.AuditActor;
import fr.expand.project.importdata.audit.AuditTrail;
import fr.expand.project.importdata.dao.Neo4jDriverProvider;
import fr.expand.project.importdata.dao.StorageConflictException;

import org.neo4j.driver.TransactionContext;

import java.time.Instant;
import java.util.*;

/** Manual, version-pinned workflow execution. Mutations serialize with model changes. */
public final class WorkflowEngine {
    public static final String ID = "_workflowId";
    public static final String VERSION = "_workflowVersion";
    public static final String STATE = "_workflowState";
    public static final String REVISION = "_workflowRevision";
    public static final String UPDATED_AT = "_workflowUpdatedAt";

    /** Caller holds the model write lock and creates the object in this same transaction. */
    public static Map<String, Object> initialProperties(
            TransactionContext tx, String key, String type) {
        var rows =
                tx.run(
                        "MATCH (b:WorkflowBinding {modelKey:$key,objectType:$type}) RETURN"
                                + " b.workflowId AS id,b.workflowVersion AS version",
                        Map.of("key", key, "type", type));
        if (!rows.hasNext()) return Map.of();
        var row = rows.single();
        var definition =
                WorkflowCatalog.load(
                        tx, key, row.get("id").asString(), row.get("version").asString());
        return Map.of(
                ID,
                definition.id(),
                VERSION,
                definition.version(),
                STATE,
                definition.initialState(),
                REVISION,
                0L,
                UPDATED_AT,
                Instant.now().toString());
    }

    /** Pure extraction for listings; never initializes or repairs legacy objects during a read. */
    public static Map<String, Object> objectState(Map<String, Object> properties) {
        if (!properties.containsKey(ID)) return null;
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("id", properties.get(ID));
        state.put("version", properties.get(VERSION));
        state.put("state", properties.get(STATE));
        state.put("revision", properties.get(REVISION));
        state.put("updatedAt", properties.get(UPDATED_AT));
        return state;
    }

    public Map<String, Object> loadObjectWorkflow(
            String key, long objectId, boolean canTransition) {
        validateIdentity(key, objectId);
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            return session.executeRead(
                    tx -> describe(tx, key, objectId, object(tx, key, objectId), canTransition));
        }
    }

    public Map<String, Object> transition(
            String key,
            long objectId,
            String objectUuid,
            String transitionId,
            long expectedRevision,
            AuditActor actor) {
        validateIdentity(key, objectId);
        if (objectUuid == null
                || objectUuid.isBlank()
                || objectUuid.length() > 128
                || transitionId == null
                || transitionId.isBlank()
                || transitionId.length() > 128
                || expectedRevision < 0)
            throw new IllegalArgumentException(
                    "Object UUID, transition and nonnegative revision are required");
        Objects.requireNonNull(actor);
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            return session.executeWrite(
                    tx -> {
                        var model =
                                tx.run(
                                        "MATCH (m:DataModel {key:$key}) SET m.key=m.key RETURN"
                                                + " m.key",
                                        Map.of("key", key));
                        if (!model.hasNext()) throw new NoSuchElementException("Model not found");
                        model.consume();
                        Map<String, Object> before = object(tx, key, objectId);
                        if (!objectUuid.equals(before.get("uuid")))
                            throw new StorageConflictException(
                                    "Object identity changed; reload the object");
                        if (!before.containsKey(ID))
                            throw new IllegalArgumentException("Object has no workflow");
                        Object rawRevision = before.get(REVISION);
                        if (!(rawRevision instanceof Number)
                                || ((Number) rawRevision).longValue() != expectedRevision)
                            throw new StorageConflictException(
                                    "Workflow changed; reload the object");
                        var definition =
                                WorkflowCatalog.load(
                                        tx,
                                        key,
                                        (String) before.get(ID),
                                        (String) before.get(VERSION));
                        var state = definition.state((String) before.get(STATE));
                        var action = definition.transition(transitionId);
                        if (state == null
                                || state.terminal()
                                || action == null
                                || !action.from().equals(state.code()))
                            throw new IllegalArgumentException(
                                    "Transition is not available from the current state");
                        long nextRevision;
                        try {
                            nextRevision = Math.addExact(expectedRevision, 1L);
                        } catch (ArithmeticException e) {
                            throw new StorageConflictException("Workflow revision exhausted");
                        }
                        Map<String, Object> parameters = new HashMap<>();
                        parameters.put("key", key);
                        parameters.put("id", objectId);
                        parameters.put("uuid", objectUuid);
                        parameters.put("workflowId", definition.id());
                        parameters.put("version", definition.version());
                        parameters.put("from", state.code());
                        parameters.put("to", action.to());
                        parameters.put("expected", expectedRevision);
                        parameters.put("next", nextRevision);
                        parameters.put("updated", Instant.now().toString());
                        var updated =
                                tx.run(
                                        "MATCH (n:DataObject {modelKey:$key,uuid:$uuid}) WHERE"
                                            + " id(n)=$id AND n._workflowId=$workflowId AND"
                                            + " n._workflowVersion=$version AND"
                                            + " n._workflowState=$from AND"
                                            + " n._workflowRevision=$expected SET"
                                            + " n._workflowState=$to,n._workflowRevision=$next,n._workflowUpdatedAt=$updated"
                                            + " RETURN properties(n) AS p",
                                        parameters);
                        if (!updated.hasNext())
                            throw new StorageConflictException(
                                    "Workflow changed; reload the object");
                        Map<String, Object> after = updated.single().get("p").asMap();
                        AuditTrail.record(
                                tx,
                                key,
                                actor,
                                "TRANSITION",
                                "OBJECT",
                                objectUuid,
                                AuditTrail.entity(objectId, before),
                                AuditTrail.entity(objectId, after));
                        return describe(tx, key, objectId, after, true);
                    });
        }
    }

    private static void validateIdentity(String key, long id) {
        if (key == null || key.isBlank() || key.length() > 512 || id < 0)
            throw new IllegalArgumentException("Invalid model or object identifier");
    }

    private static Map<String, Object> object(TransactionContext tx, String key, long id) {
        var rows =
                tx.run(
                        "MATCH (n:DataObject {modelKey:$key}) WHERE id(n)=$id RETURN properties(n)"
                                + " AS p",
                        Map.of("key", key, "id", id));
        if (!rows.hasNext()) throw new NoSuchElementException("Object not found");
        return rows.single().get("p").asMap();
    }

    private static Map<String, Object> describe(
            TransactionContext tx,
            String key,
            long objectId,
            Map<String, Object> properties,
            boolean canTransition) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("objectId", objectId);
        result.put("canTransition", canTransition);
        result.put("objectUuid", properties.get("uuid"));
        Map<String, Object> workflow = objectState(properties);
        result.put("workflow", workflow);
        List<Map<String, Object>> transitions = new ArrayList<>();
        result.put("transitions", transitions);
        if (workflow == null) return result;
        var definition =
                WorkflowCatalog.load(
                        tx, key, (String) properties.get(ID), (String) properties.get(VERSION));
        var state = definition.state((String) properties.get(STATE));
        workflow.put("label", definition.label());
        workflow.put("stateLabel", state == null ? properties.get(STATE) : state.label());
        workflow.put("terminal", state != null && state.terminal());
        if (canTransition && state != null && !state.terminal()) {
            for (var action : definition.transitionsFrom(state.code())) {
                var target = definition.state(action.to());
                transitions.add(
                        Map.of(
                                "id",
                                action.id(),
                                "label",
                                action.label(),
                                "from",
                                action.from(),
                                "to",
                                action.to(),
                                "toLabel",
                                target.label()));
            }
        }
        return result;
    }
}
