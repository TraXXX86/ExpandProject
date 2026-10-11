package fr.expand.project.importdata.storage;

import static org.junit.Assert.*;

import fr.expand.project.importdata.api.impl.ModelBasedImportAPI;
import fr.expand.project.importdata.audit.AuditActor;
import fr.expand.project.importdata.dao.*;
import fr.expand.project.importdata.data.*;
import fr.expand.project.importdata.dto.generated.*;
import fr.expand.project.importdata.imports.*;
import fr.expand.project.importdata.model.*;
import fr.expand.project.importdata.workflow.*;

import org.junit.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Cross-feature tests against a uniquely scoped graph; workflows are never ordinary attributes. */
public class WorkflowStorageIT {
    private String key, xml;
    private ModelManager manager;
    private Neo4jModelStore models;
    private Neo4jDataStore data;
    private final WorkflowCatalog catalog = new WorkflowCatalog();
    private final ImportWorkflowStore imports = new ImportWorkflowStore();

    @Before
    public void setup() throws Exception {
        String name = "WorkflowStorageIT_" + UUID.randomUUID().toString().replace("-", "");
        key = name + ":1";
        xml =
                """
<DATA_MODEL NAME="%s" VERSION="1"><OBJECT_TYPES>
<OBJECT_TYPE NAME="PERSON"><ATTRIBUTE_DEFINITIONS>
<ATTRIBUTE_DEFINITION NAME="NAME" TYPE="STRING" REQUIRED="true" SEARCHABLE="true"/>
</ATTRIBUTE_DEFINITIONS></OBJECT_TYPE>
<OBJECT_TYPE NAME="OTHER"><ATTRIBUTE_DEFINITIONS><ATTRIBUTE_DEFINITION NAME="NAME" TYPE="STRING"/></ATTRIBUTE_DEFINITIONS></OBJECT_TYPE>
</OBJECT_TYPES><LINK_TYPES><LINK_TYPE NAME="KNOWS" DIRECTED="true">
<SOURCE_TYPES><TYPE_REF NAME="PERSON"/></SOURCE_TYPES><TARGET_TYPES><TYPE_REF NAME="PERSON"/></TARGET_TYPES>
</LINK_TYPE></LINK_TYPES></DATA_MODEL>
"""
                        .formatted(name);
        manager = new ModelManager();
        manager.loadModelFromXml(xml);
        models = new Neo4jModelStore();
        models.createModel(manager.getCurrentModel(), xml);
        data = new Neo4jDataStore();
    }

    @After
    public void cleanup() {
        if (models != null) models.deleteModelAndDataByKey(key);
    }

    private String definition(String version) {
        return """
<WORKFLOW ID="review" VERSION="%s" LABEL="Review" INITIAL_STATE="draft">
<OBJECT_TYPES><TYPE_REF NAME="PERSON"/></OBJECT_TYPES>
<STATES><STATE CODE="draft" LABEL="Draft"/><STATE CODE="done" LABEL="Done" TERMINAL="true"/></STATES>
<TRANSITIONS><TRANSITION ID="finish" FROM="draft" TO="done" LABEL="Finish"/></TRANSITIONS>
</WORKFLOW>
"""
                .formatted(version);
    }

    private void activate(String version) {
        catalog.upload(key, definition(version), AuditActor.system());
        var review = catalog.activate(key, "review", version, null, false, AuditActor.system());
        catalog.activate(
                key,
                "review",
                version,
                (String) review.get("previewHash"),
                true,
                AuditActor.system());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> state(long id) {
        return (Map<String, Object>) data.loadObjectById(key, id).get("workflow");
    }

    private ImportInput csv(String value) {
        return ImportInput.table(
                TabularFile.csv(value.getBytes(StandardCharsets.UTF_8)),
                "PERSON",
                "id",
                Map.of("NAME", "name"));
    }

    private void commit(ImportInput input, String mode) {
        var preview = imports.preview(key, manager, input, mode);
        assertTrue(preview.rows().toString(), preview.valid());
        imports.commit(
                key, manager, input, mode, preview.previewHash(), true, true, AuditActor.system());
    }

    @Test
    public void initializationMetadataFilteringAndGenericUpdatesAreIsolated() {
        long old = data.createObject(key, "PERSON", Map.of("NAME", "Legacy"), 1);
        activate("1");
        long first = data.createObject(key, "PERSON", Map.of("NAME", "Alice"), 2);
        long second = data.createObject(key, "PERSON", Map.of("NAME", "Alice Bob"), 3);
        assertNull(state(old));
        assertEquals("draft", state(first).get("state"));
        assertEquals(0L, state(first).get("revision"));
        assertNotNull(state(first).get("updatedAt"));
        String uuid = (String) data.loadObjectById(key, second).get("uuid");
        new WorkflowEngine().transition(key, second, uuid, "finish", 0, AuditActor.system());
        data.updateObject(key, second, Map.of("NAME", "Alice Updated"));
        assertEquals("done", state(second).get("state"));
        assertEquals(1L, state(second).get("revision"));
        assertThrows(
                IllegalArgumentException.class,
                () -> data.updateObject(key, first, Map.of("_workflowState", "done")));
        assertFalse(
                data.loadObjectById(key, first).get("attributes").toString().contains("_workflow"));
        for (String mode : List.of("contains", "fulltext")) {
            var page = data.loadDataPage(key, 0, 1, "Alice", "PERSON", mode, "done", "review");
            assertEquals(1L, page.get("totalObjects"));
            assertEquals(false, page.get("hasMore"));
            assertEquals(
                    second,
                    ((Number) ((Map<?, ?>) ((List<?>) page.get("objects")).get(0)).get("id"))
                            .longValue());
        }
        assertEquals(
                1L,
                data.loadDataPage(key, 0, 20, "", "", "contains", "__unassigned__", null)
                        .get("totalObjects"));
        assertEquals(
                0L,
                data.loadDataPage(key, 0, 20, "", "", "contains", "draft", "wrong")
                        .get("totalObjects"));
        data.createLink(key, first, second, "KNOWS", true, Map.of());
        assertTrue(data.neighbors(key, first, 10).toString().contains("workflow="));
        assertTrue(
                new GraphInsightsStore()
                        .paths(key, first, second, 2, Set.of(), true, manager)
                        .toString()
                        .contains("state=done"));
    }

    @Test
    public void xmlAndReviewedImportsInitializeButUpsertPreservesPinnedState() {
        activate("1");
        var pack = new DATAS();
        pack.setOBJECTS(new OBJECTS());
        pack.setLINKS(new LINKS());
        var object = new OBJECT();
        object.setTYPE("PERSON");
        object.setID(10);
        var attr = new ATTRIBUTE();
        attr.setKEY("NAME");
        attr.setVALUE("XML");
        object.getATTRIBUTE().add(attr);
        pack.getOBJECTS().getOBJECT().add(object);
        try (var api = new ModelBasedImportAPI(manager)) {
            assertTrue(api.importData(pack, false, key).isValid());
        }
        var incoming = csv("id,name\n11,CSV");
        var preview = imports.preview(key, manager, incoming, "create");
        assertEquals("draft", preview.rows().get(0).workflowAfter().get("state"));
        assertFalse(preview.rows().get(0).after().containsKey("_workflowState"));
        assertEquals(
                preview.previewHash(),
                imports.preview(key, manager, incoming, "create").previewHash());
        imports.commit(
                key,
                manager,
                incoming,
                "create",
                preview.previewHash(),
                true,
                true,
                AuditActor.system());
        assertEquals(2, data.loadObjects(key).size());
        for (var row : data.loadObjects(key))
            assertEquals("draft", ((Map<?, ?>) row.get("workflow")).get("state"));
        var row =
                data.loadObjects(key).stream()
                        .filter(r -> ((Number) r.get("dataId")).intValue() == 11)
                        .findFirst()
                        .orElseThrow();
        long id = ((Number) row.get("id")).longValue();
        new WorkflowEngine()
                .transition(key, id, (String) row.get("uuid"), "finish", 0, AuditActor.system());
        activate("2");
        commit(csv("id,name\n11,Changed\n12,New"), "upsert");
        assertEquals("done", state(id).get("state"));
        assertEquals("1", state(id).get("version"));
        var newRow =
                data.loadObjects(key).stream()
                        .filter(r -> ((Number) r.get("dataId")).intValue() == 12)
                        .findFirst()
                        .orElseThrow();
        assertEquals("2", ((Map<?, ?>) newRow.get("workflow")).get("version"));
        assertEquals(
                0,
                ((Map<?, ?>) new GraphInsightsStore().quality(key, manager).get("issueCounts"))
                        .get("workflow_drift"));
    }

    @Test
    public void bindingAndDefinitionChangesInvalidateReviewedImports() {
        var incoming = csv("id,name\n1,Reviewed");
        var before = imports.preview(key, manager, incoming, "create");
        activate("1");
        assertThrows(
                StorageConflictException.class,
                () ->
                        imports.commit(
                                key,
                                manager,
                                incoming,
                                "create",
                                before.previewHash(),
                                true,
                                true,
                                AuditActor.system()));
        assertTrue(data.loadObjects(key).isEmpty());
        var next = imports.preview(key, manager, incoming, "create");
        catalog.upload(key, definition("2"), AuditActor.system());
        assertThrows(
                StorageConflictException.class,
                () ->
                        imports.commit(
                                key,
                                manager,
                                incoming,
                                "create",
                                next.previewHash(),
                                true,
                                true,
                                AuditActor.system()));
        commit(incoming, "create");
        var unchanged = imports.preview(key, manager, incoming, "upsert");
        catalog.deactivate(key, List.of("PERSON"), AuditActor.system());
        assertThrows(
                StorageConflictException.class,
                () ->
                        imports.commit(
                                key,
                                manager,
                                incoming,
                                "upsert",
                                unchanged.previewHash(),
                                true,
                                true,
                                AuditActor.system()));
    }

    @Test
    public void qualityFindsWorkflowCorruptionAndModelDeletionCleansOnlyItsExtensions()
            throws Exception {
        activate("1");
        long id = data.createObject(key, "PERSON", Map.of("NAME", "Bad"));
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            session.run(
                            "MATCH (n:DataObject {modelKey:$key}) WHERE id(n)=$id SET"
                                    + " n._workflowState='missing'",
                            Map.of("key", key, "id", id))
                    .consume();
        }
        assertEquals(
                1,
                ((Map<?, ?>) new GraphInsightsStore().quality(key, manager).get("issueCounts"))
                        .get("workflow_drift"));
        var removed = new ModelManager();
        removed.loadModelFromXml(xml.replace("PERSON", "REPLACED"));
        assertThrows(
                StorageConflictException.class,
                () ->
                        models.updateModel(
                                key, removed.getCurrentModel(), removed.getCurrentModelXml()));
        models.deleteModelAndDataByKey(key);
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            assertEquals(
                    0L,
                    session.run(
                                    "MATCH (n {modelKey:$key}) WHERE n:WorkflowBinding OR"
                                            + " n:WorkflowDefinition RETURN count(n) AS count",
                                    Map.of("key", key))
                            .single()
                            .get("count")
                            .asLong());
            assertTrue(
                    session.run(
                                            "MATCH (n:AuditData {modelKey:$key}) RETURN count(n) AS"
                                                    + " count",
                                            Map.of("key", key))
                                    .single()
                                    .get("count")
                                    .asLong()
                            > 0);
        }
    }
}
