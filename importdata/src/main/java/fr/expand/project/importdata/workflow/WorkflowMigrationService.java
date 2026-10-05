package fr.expand.project.importdata.workflow;

import fr.expand.project.importdata.audit.*;
import fr.expand.project.importdata.dao.*;

import java.time.Instant;
import java.util.*;

/**
 * Bounded explicit initialization/migration, reviewed against object revisions and model metadata.
 */
public final class WorkflowMigrationService {
    public record Request(
            String modelKey,
            String id,
            String version,
            String mode,
            List<String> objectTypes,
            List<Long> objectIds,
            String sourceId,
            String sourceVersion,
            Map<String, String> stateMapping,
            String previewHash) {
        public Request {
            objectTypes = objectTypes == null ? List.of() : List.copyOf(objectTypes);
            objectIds = objectIds == null ? List.of() : List.copyOf(objectIds);
            stateMapping =
                    stateMapping == null
                            ? Map.of()
                            : Collections.unmodifiableMap(new TreeMap<>(stateMapping));
        }
    }

    public Map<String, Object> execute(Request request, boolean commit, AuditActor actor) {
        if (!Set.of("initialize", "migrate").contains(request.mode()))
            throw new IllegalArgumentException("Mode must be initialize or migrate");
        if (request.objectIds().size() > 1000
                || new HashSet<>(request.objectIds()).size() != request.objectIds().size())
            throw new IllegalArgumentException("Select at most 1000 distinct objects");
        if (commit && request.objectIds().isEmpty())
            throw new IllegalArgumentException("Commit requires explicit objectIds from preview");
        if (request.objectTypes().size() > 1000)
            throw new IllegalArgumentException("Too many object types");
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            org.neo4j.driver.TransactionCallback<Map<String, Object>> work =
                    tx -> {
                        String xml =
                                commit
                                        ? WorkflowCatalog.lock(tx, request.modelKey())
                                        : WorkflowCatalog.model(tx, request.modelKey())
                                                .getCurrentModelXml();
                        var target =
                                WorkflowCatalog.load(
                                        tx, request.modelKey(), request.id(), request.version());
                        var allowed =
                                WorkflowCatalog.expandTypes(
                                        target, WorkflowCatalog.model(tx, request.modelKey()));
                        var types =
                                request.objectTypes().isEmpty()
                                        ? allowed
                                        : new TreeSet<>(request.objectTypes()).stream().toList();
                        if (!allowed.containsAll(types))
                            throw new IllegalArgumentException(
                                    "Selected object type is not supported by target workflow");
                        for (var state : request.stateMapping().values())
                            if (target.state(state) == null)
                                throw new IllegalArgumentException(
                                        "Unknown destination state: " + state);
                        var params = new HashMap<String, Object>();
                        params.put("key", request.modelKey());
                        params.put("types", types);
                        params.put("ids", request.objectIds());
                        params.put("sourceId", Objects.requireNonNullElse(request.sourceId(), ""));
                        params.put(
                                "sourceVersion",
                                Objects.requireNonNullElse(request.sourceVersion(), ""));
                        String condition =
                                request.mode().equals("initialize")
                                        ? "n._workflowId IS NULL"
                                        : "n._workflowId IS NOT NULL AND ($sourceId='' OR"
                                                + " n._workflowId=$sourceId) AND ($sourceVersion=''"
                                                + " OR n._workflowVersion=$sourceVersion)";
                        var rows =
                                tx.run(
                                                "MATCH (n:DataObject {modelKey:$key}) WHERE n.type"
                                                        + " IN $types AND (size($ids)=0 OR id(n) IN"
                                                        + " $ids) AND "
                                                        + condition
                                                        + " RETURN id(n) AS id,properties(n) AS"
                                                        + " p,elementId(n) AS elementId ORDER BY id"
                                                        + " LIMIT 1001",
                                                params)
                                        .list();
                        boolean hasMore = rows.size() > 1000;
                        if (hasMore) rows = rows.subList(0, 1000);
                        if (!request.objectIds().isEmpty()
                                && rows.size() != request.objectIds().size())
                            throw new StorageConflictException(
                                    "Some selected objects are missing or no longer eligible;"
                                            + " preview again");
                        var items = new ArrayList<Map<String, Object>>();
                        for (var row : rows) {
                            var props = row.get("p").asMap();
                            String oldState = (String) props.get("_workflowState");
                            String to =
                                    request.mode().equals("initialize")
                                            ? target.initialState()
                                            : request.stateMapping().get(oldState);
                            if (to == null)
                                throw new IllegalArgumentException(
                                        "A mapping is required for source state: " + oldState);

                            var item = new LinkedHashMap<String, Object>();
                            item.put("id", row.get("id").asLong());
                            item.put("uuid", props.get("uuid"));
                            item.put("elementId", row.get("elementId").asString());
                            item.put("objectType", props.get("type"));
                            item.put("fromWorkflowId", props.get("_workflowId"));
                            item.put("fromVersion", props.get("_workflowVersion"));
                            item.put("fromState", oldState);
                            item.put("toState", to);
                            item.put("revision", props.getOrDefault("_workflowRevision", 0L));
                            items.add(item);
                        }
                        String hash =
                                WorkflowCatalog.hash(
                                        Arrays.asList(
                                                request.modelKey(),
                                                xml,
                                                WorkflowCatalog.fingerprint(tx, request.modelKey()),
                                                request.id(),
                                                request.version(),
                                                request.mode(),
                                                types,
                                                request.sourceId(),
                                                request.sourceVersion(),
                                                request.stateMapping(),
                                                items));
                        if (commit) {
                            WorkflowCatalog.requireHash(hash, request.previewHash());
                            for (var item : items) {
                                long objectId = ((Number) item.get("id")).longValue();
                                long nextRevision;
                                try {
                                    nextRevision =
                                            Math.addExact(
                                                    ((Number) item.get("revision")).longValue(),
                                                    1L);
                                } catch (ArithmeticException e) {
                                    throw new StorageConflictException(
                                            "Workflow revision exhausted");
                                }
                                var before = AuditTrail.object(tx, request.modelKey(), objectId);
                                tx.run(
                                                "MATCH (n:DataObject {modelKey:$key}) WHERE"
                                                    + " id(n)=$objectId AND elementId(n)=$elementId"
                                                    + " SET n.uuid=coalesce(n.uuid,$uuid),n._workflowId=$id,n._workflowVersion=$version,n._workflowState=$state,n._workflowRevision=$revision,n._workflowUpdatedAt=$at",
                                                Map.of(
                                                        "key",
                                                        request.modelKey(),
                                                        "objectId",
                                                        objectId,
                                                        "uuid",
                                                        item.get("uuid") == null
                                                                ? UUID.randomUUID().toString()
                                                                : item.get("uuid"),
                                                        "elementId",
                                                        item.get("elementId"),
                                                        "id",
                                                        request.id(),
                                                        "version",
                                                        request.version(),
                                                        "state",
                                                        item.get("toState"),
                                                        "at",
                                                        Instant.now().toString(),
                                                        "revision",
                                                        nextRevision))
                                        .consume();
                                var after = AuditTrail.object(tx, request.modelKey(), objectId);
                                AuditTrail.record(
                                        tx,
                                        request.modelKey(),
                                        actor,
                                        request.mode().equals("initialize")
                                                ? "INITIALIZE"
                                                : "MIGRATE",
                                        "OBJECT",
                                        AuditTrail.identity(after),
                                        before,
                                        after);
                            }
                        }
                        return Map.<String, Object>of(
                                "previewHash",
                                hash,
                                "items",
                                items,
                                "hasMore",
                                hasMore,
                                "limit",
                                1000,
                                "committed",
                                commit,
                                "count",
                                items.size());
                    };
            return commit ? session.executeWrite(work) : session.executeRead(work);
        }
    }
}
