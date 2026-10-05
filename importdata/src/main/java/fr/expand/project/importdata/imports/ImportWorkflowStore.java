package fr.expand.project.importdata.imports;

import com.google.gson.Gson;

import fr.expand.project.importdata.audit.AuditActor;
import fr.expand.project.importdata.audit.AuditTrail;
import fr.expand.project.importdata.dao.Neo4jDriverProvider;
import fr.expand.project.importdata.dao.StorageConflictException;
import fr.expand.project.importdata.dto.generated.*;
import fr.expand.project.importdata.model.ModelManager;
import fr.expand.project.importdata.util.CypherUtils;
import fr.expand.project.importdata.util.SearchIndex;
import fr.expand.project.importdata.validation.DataValidator;
import fr.expand.project.importdata.workflow.WorkflowCatalog;
import fr.expand.project.importdata.workflow.WorkflowEngine;

import org.neo4j.driver.*;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Preview and atomic commit use the same planner; a model lock serializes all data writers. */
public final class ImportWorkflowStore {
    private static final Gson JSON = new Gson();

    public record Change(
            int row,
            String entityType,
            String action,
            String type,
            Integer dataId,
            Map<String, Object> before,
            Map<String, Object> after,
            List<String> errors,
            ImportInput.Identity from,
            ImportInput.Identity to,
            Map<String, Object> workflowBefore,
            Map<String, Object> workflowAfter) {}

    public record Preview(
            boolean valid, String previewHash, Map<String, Long> summary, List<Change> rows) {}

    public record Commit(boolean imported, String operationId, Map<String, Long> summary) {}

    private record Plan(
            Preview preview, List<Map<String, Object>> stored, List<String> references) {}

    public Preview preview(String key, ModelManager manager, ImportInput input, String mode) {
        try (Session session = Neo4jDriverProvider.getDriver().session()) {
            return session.executeRead(tx -> plan(tx, key, manager, input, mode).preview());
        }
    }

    public Commit commit(
            String key,
            ModelManager manager,
            ImportInput input,
            String mode,
            String expectedHash,
            boolean canCreate,
            boolean canUpdate,
            AuditActor actor) {
        if (expectedHash == null || !expectedHash.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Prévisualisation requise");
        try (Session session = Neo4jDriverProvider.getDriver().session()) {
            return session.executeWrite(
                    tx -> {
                        var model =
                                tx.run(
                                        "MATCH (m:DataModel {key:$key}) SET m.key=m.key RETURN"
                                                + " m.xml AS xml",
                                        Map.of("key", key));
                        if (!model.hasNext()
                                || !manager.getCurrentModelXml()
                                        .equals(model.single().get("xml").asString("")))
                            throw new StorageConflictException(
                                    "Le modèle a changé ; relancez la prévisualisation");
                        Plan plan = plan(tx, key, manager, input, mode);
                        if (!MessageDigest.isEqual(
                                expectedHash.getBytes(StandardCharsets.US_ASCII),
                                plan.preview.previewHash.getBytes(StandardCharsets.US_ASCII)))
                            throw new StorageConflictException(
                                    "Les données ont changé ; relancez la prévisualisation");
                        if (!plan.preview.valid)
                            throw new IllegalArgumentException(
                                    "Corrigez les conflits avant l'import");
                        if (plan.preview.summary.get("additions") > 0 && !canCreate
                                || plan.preview.summary.get("updates") > 0 && !canUpdate)
                            throw new io.javalin.http.ForbiddenResponse(
                                    "Droit de création ou modification refusé");
                        var searchFields = SearchIndex.fields(manager.getCurrentModel());
                        Map<String, Map<String, Object>> workflowDefaults = new HashMap<>();
                        for (int i = 0; i < plan.preview.rows.size(); i++) {
                            Change change = plan.preview.rows.get(i);
                            if (change.action.equals("UNCHANGED")) continue;
                            Map<String, Object> properties = new TreeMap<>(plan.stored.get(i));
                            properties.putAll(change.after);
                            properties.put("modelKey", key);
                            properties.putIfAbsent("uuid", UUID.randomUUID().toString());
                            if (change.entityType.equals("OBJECT")) {
                                properties.put("dataId", change.dataId);
                                properties.put("type", change.type);
                                if (change.action.equals("CREATE"))
                                    properties.putAll(
                                            workflowDefaults.computeIfAbsent(
                                                    change.type,
                                                    type ->
                                                            WorkflowEngine.initialProperties(
                                                                    tx, key, type)));
                                properties.put(
                                        "searchText",
                                        SearchIndex.text(change.type, properties, searchFields));
                                if (change.action.equals("CREATE"))
                                    tx.run(
                                                    "CREATE (n:DataObject:"
                                                            + CypherUtils.identifier(change.type)
                                                            + ") SET n=$props",
                                                    Map.of("props", properties))
                                            .consume();
                                else
                                    requireUpdated(
                                            tx.run(
                                                    "MATCH (n:DataObject {modelKey:$key}) WHERE"
                                                        + " elementId(n)=$ref SET n=$props RETURN"
                                                        + " count(n) AS affected",
                                                    Map.of(
                                                            "key",
                                                            key,
                                                            "ref",
                                                            plan.references.get(i),
                                                            "props",
                                                            properties)));
                            } else {
                                properties.put("linkType", change.type);
                                properties.put(
                                        "directed", manager.getLinkType(change.type).isDIRECTED());
                                if (change.action.equals("CREATE")) {
                                    tx.run(
                                                    "MATCH (a:DataObject"
                                                        + " {modelKey:$key,type:$aType,dataId:$aId}),(b:DataObject"
                                                        + " {modelKey:$key,type:$bType,dataId:$bId})"
                                                        + " CREATE (a)-[r:"
                                                            + CypherUtils.identifier(change.type)
                                                            + "]->(b) SET r=$props",
                                                    Map.of(
                                                            "key",
                                                            key,
                                                            "aType",
                                                            change.from.type(),
                                                            "aId",
                                                            change.from.dataId(),
                                                            "bType",
                                                            change.to.type(),
                                                            "bId",
                                                            change.to.dataId(),
                                                            "props",
                                                            properties))
                                            .consume();
                                } else
                                    requireUpdated(
                                            tx.run(
                                                    "MATCH (:DataObject"
                                                        + " {modelKey:$key})-[r]->(:DataObject"
                                                        + " {modelKey:$key}) WHERE"
                                                        + " elementId(r)=$ref SET r=$props RETURN"
                                                        + " count(r) AS affected",
                                                    Map.of(
                                                            "key",
                                                            key,
                                                            "ref",
                                                            plan.references.get(i),
                                                            "props",
                                                            properties)));
                            }
                            AuditTrail.record(
                                    tx,
                                    key,
                                    actor,
                                    change.action,
                                    change.entityType,
                                    properties.get("uuid").toString(),
                                    auditSnapshot(change.before, change.workflowBefore),
                                    auditSnapshot(
                                            change.after,
                                            change.entityType.equals("OBJECT")
                                                    ? WorkflowEngine.objectState(properties)
                                                    : null));
                        }
                        AuditTrail.record(
                                tx,
                                key,
                                actor,
                                "IMPORT",
                                "IMPORT",
                                actor.operationId(),
                                null,
                                plan.preview.summary);
                        return new Commit(true, actor.operationId(), plan.preview.summary);
                    });
        }
    }

    private Plan plan(
            TransactionContext tx,
            String key,
            ModelManager manager,
            ImportInput input,
            String mode) {
        if (!Set.of("create", "upsert").contains(mode))
            throw new IllegalArgumentException("Mode create ou upsert requis");
        List<Change> changes = new ArrayList<>();
        List<Map<String, Object>> stored = new ArrayList<>();
        List<String> references = new ArrayList<>();
        Map<String, Map<String, Object>> workflowDefaults = new HashMap<>();
        Set<String> identities = new HashSet<>(), incomingObjects = new HashSet<>();
        for (var entry : input.entries())
            if (entry.entityType().equals("OBJECT") && entry.dataId() != null)
                incomingObjects.add(entry.type() + "/" + entry.dataId());
        DataValidator validator = new DataValidator(manager);
        // Objects precede links for both planning and creation.
        var sorted =
                input.entries().stream()
                        .sorted(Comparator.comparing(e -> e.entityType().equals("OBJECT") ? 0 : 1))
                        .toList();
        for (var entry : sorted) {
            List<String> errors = new ArrayList<>(entry.errors());
            Map<String, Object> current = new TreeMap<>();
            String reference = "";
            String fromIdentity = Objects.toString(entry.from(), ""),
                    toIdentity = Objects.toString(entry.to(), "");
            var linkDefinition = manager.getLinkType(entry.type());
            boolean directed = linkDefinition == null || linkDefinition.isDIRECTED();
            if (!directed && fromIdentity.compareTo(toIdentity) > 0) {
                String swap = fromIdentity;
                fromIdentity = toIdentity;
                toIdentity = swap;
            }
            String identity =
                    entry.entityType()
                            + ":"
                            + entry.type()
                            + ":"
                            + entry.dataId()
                            + ":"
                            + fromIdentity
                            + ":"
                            + toIdentity;
            if (!identities.add(identity)) errors.add("Identité dupliquée dans le fichier");
            try {
                CypherUtils.identifier(entry.type());
                entry.attributes().keySet().forEach(CypherUtils::validateAttributeKey);
            } catch (IllegalArgumentException e) {
                errors.add(e.getMessage());
            }
            if (entry.entityType().equals("OBJECT") && entry.dataId() != null) {
                var found =
                        tx.run(
                                        "MATCH (n:DataObject {modelKey:$key,type:$type,dataId:$id})"
                                                + " RETURN properties(n) AS p, elementId(n) AS ref"
                                                + " LIMIT 2",
                                        Map.of(
                                                "key",
                                                key,
                                                "type",
                                                entry.type(),
                                                "id",
                                                entry.dataId()))
                                .list();
                if (found.size() > 1) errors.add("Identité ambiguë en base");
                if (!found.isEmpty()) {
                    current.putAll(found.get(0).get("p").asMap());
                    reference = found.get(0).get("ref").asString();
                }
            } else if (entry.entityType().equals("LINK")) {
                validateEndpoint(tx, key, manager, entry, true, incomingObjects, errors);
                validateEndpoint(tx, key, manager, entry, false, incomingObjects, errors);
                var found =
                        tx.run(
                                        "MATCH (a:DataObject"
                                                + " {modelKey:$key,type:$aType,dataId:$aId})-[r]"
                                                + (directed ? "->" : "-")
                                                + "(b:DataObject"
                                                + " {modelKey:$key,type:$bType,dataId:$bId}) WHERE"
                                                + " type(r)=$type RETURN DISTINCT properties(r) AS"
                                                + " p, elementId(r) AS ref LIMIT 2",
                                        Map.of(
                                                "key",
                                                key,
                                                "aType",
                                                entry.from().type(),
                                                "aId",
                                                entry.from().dataId(),
                                                "bType",
                                                entry.to().type(),
                                                "bId",
                                                entry.to().dataId(),
                                                "type",
                                                entry.type()))
                                .list();
                if (found.size() > 1) errors.add("Plusieurs liens correspondent à cette identité");
                if (!found.isEmpty()) {
                    current.putAll(found.get(0).get("p").asMap());
                    reference = found.get(0).get("ref").asString();
                }
            }
            Map<String, Object> before = attributes(current), after = new TreeMap<>(before);
            after.putAll(entry.attributes());
            try {
                List<ATTRIBUTE> attrs = new ArrayList<>();
                for (var item : after.entrySet()) {
                    ATTRIBUTE a = new ATTRIBUTE();
                    a.setKEY(item.getKey());
                    a.setVALUE(Objects.toString(item.getValue(), ""));
                    attrs.add(a);
                }
                fr.expand.project.importdata.validation.ValidationResult validation;
                if (entry.entityType().equals("OBJECT")) {
                    OBJECT obj = new OBJECT();
                    obj.setTYPE(entry.type());
                    obj.setID(entry.dataId() == null ? 0 : entry.dataId());
                    obj.getATTRIBUTE().addAll(attrs);
                    validation = validator.validateObjectAttributes(obj);
                    attrs = obj.getATTRIBUTE();
                } else {
                    LINK link = new LINK();
                    link.setTYPE(entry.type());
                    link.getATTRIBUTE().addAll(attrs);
                    validation = validator.validateLinkAttributes(link);
                    attrs = link.getATTRIBUTE();
                }
                validation.getErrors().forEach(e -> errors.add(e.getMessage()));
                // Validator may append model defaults to the JAXB list, see normalized attributes
                // below.
                after.clear();
                attrs.forEach(a -> after.put(a.getKEY(), Objects.toString(a.getVALUE(), "")));
            } catch (IllegalArgumentException e) {
                errors.add(e.getMessage());
            }
            if (!current.isEmpty() && mode.equals("create"))
                errors.add("Cette identité existe déjà ; utilisez le mode mise à jour");
            String action =
                    !errors.isEmpty()
                            ? "CONFLICT"
                            : current.isEmpty()
                                    ? "CREATE"
                                    : before.equals(after) ? "UNCHANGED" : "UPDATE";
            Map<String, Object> workflowBefore = WorkflowEngine.objectState(current);
            Map<String, Object> workflowAfter = workflowBefore;
            if (current.isEmpty() && entry.entityType().equals("OBJECT") && errors.isEmpty()) {
                current.putAll(
                        workflowDefaults.computeIfAbsent(
                                entry.type(),
                                type -> WorkflowEngine.initialProperties(tx, key, type)));
                // Initialization time is assigned at commit, not part of a reusable preview digest.
                current.remove("_workflowUpdatedAt");
                workflowAfter = WorkflowEngine.objectState(current);
            }
            changes.add(
                    new Change(
                            entry.row(),
                            entry.entityType(),
                            action,
                            entry.type(),
                            entry.dataId(),
                            before,
                            after,
                            errors,
                            entry.from(),
                            entry.to(),
                            workflowBefore,
                            workflowAfter));
            stored.add(current);
            references.add(reference);
        }
        Map<String, Long> counts = new TreeMap<>();
        for (var pair :
                Map.of(
                                "additions",
                                "CREATE",
                                "updates",
                                "UPDATE",
                                "unchanged",
                                "UNCHANGED",
                                "conflicts",
                                "CONFLICT")
                        .entrySet())
            counts.put(
                    pair.getKey(),
                    changes.stream().filter(c -> c.action.equals(pair.getValue())).count());
        counts.put("objects", changes.stream().filter(c -> c.entityType.equals("OBJECT")).count());
        counts.put("links", changes.stream().filter(c -> c.entityType.equals("LINK")).count());
        String digest =
                hash(
                        JSON.toJson(
                                List.of(
                                        key,
                                        manager.getCurrentModelXml(),
                                        WorkflowCatalog.fingerprint(tx, key),
                                        mode,
                                        changes,
                                        stored,
                                        references)));
        return new Plan(
                new Preview(counts.get("conflicts") == 0, digest, counts, changes),
                stored,
                references);
    }

    private static Map<String, Object> auditSnapshot(
            Map<String, Object> attrs, Map<String, Object> workflow) {
        Map<String, Object> result = new LinkedHashMap<>(attrs);
        if (workflow != null) {
            result.put(WorkflowEngine.ID, workflow.get("id"));
            result.put(WorkflowEngine.VERSION, workflow.get("version"));
            result.put(WorkflowEngine.STATE, workflow.get("state"));
            result.put(WorkflowEngine.REVISION, workflow.get("revision"));
            result.put(WorkflowEngine.UPDATED_AT, workflow.get("updatedAt"));
        }
        return result;
    }

    private static void requireUpdated(Result result) {
        if (result.single().get("affected").asLong() != 1)
            throw new StorageConflictException("Identité modifiée pendant l’import");
    }

    private static Map<String, Object> attributes(Map<String, Object> props) {
        Map<String, Object> attrs = new TreeMap<>();
        props.forEach(
                (k, v) -> {
                    if (!CypherUtils.isReservedProperty(k)) attrs.put(k, v);
                });
        return attrs;
    }

    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void validateEndpoint(
            TransactionContext tx,
            String key,
            ModelManager manager,
            ImportInput.Entry entry,
            boolean source,
            Set<String> incoming,
            List<String> errors) {
        var identity = source ? entry.from() : entry.to();
        var definition = manager.getLinkType(entry.type());
        boolean allowed = false;
        if (definition != null) {
            var refs =
                    source
                            ? (definition.getSOURCETYPES() == null
                                    ? List
                                            .<fr.expand.project.importdata.model.generated.TYPEREF>
                                                    of()
                                    : definition.getSOURCETYPES().getTYPEREF())
                            : (definition.getTARGETTYPES() == null
                                    ? List
                                            .<fr.expand.project.importdata.model.generated.TYPEREF>
                                                    of()
                                    : definition.getTARGETTYPES().getTYPEREF());
            allowed =
                    refs.stream()
                            .anyMatch(
                                    ref -> manager.isTypeOrSubtype(identity.type(), ref.getNAME()));
        }
        if (!allowed) errors.add("Type d'extrémité non autorisé : " + identity.type());
        if (!incoming.contains(identity.key())
                && !tx.run(
                                "MATCH (n:DataObject {modelKey:$key,type:$type,dataId:$id}) RETURN"
                                        + " n LIMIT 1",
                                Map.of(
                                        "key",
                                        key,
                                        "type",
                                        identity.type(),
                                        "id",
                                        identity.dataId()))
                        .hasNext()) errors.add("Objet lié introuvable : " + identity.key());
    }
}
