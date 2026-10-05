package fr.expand.project.importdata.workflow;

import static org.junit.Assert.*;

import fr.expand.project.importdata.audit.AuditActor;
import fr.expand.project.importdata.dao.*;
import fr.expand.project.importdata.model.*;

import org.junit.*;

import java.util.*;

public class WorkflowCatalogIT {
    private String key, xml;
    private final WorkflowCatalog catalog = new WorkflowCatalog();
    private final AuditActor actor = AuditActor.system();

    @Before
    public void setup() throws Exception {
        String name = "WorkflowCatalogIT_" + UUID.randomUUID().toString().replace("-", "");
        key = name + ":1";
        xml =
                "<DATA_MODEL NAME=\""
                        + name
                        + "\" VERSION=\"1\"><OBJECT_TYPES><OBJECT_TYPE"
                        + " NAME=\"PERSON\"><ATTRIBUTE_DEFINITIONS><ATTRIBUTE_DEFINITION"
                        + " NAME=\"NAME\""
                        + " TYPE=\"STRING\"/></ATTRIBUTE_DEFINITIONS></OBJECT_TYPE><OBJECT_TYPE"
                        + " NAME=\"EMPLOYEE\""
                        + " PARENT=\"PERSON\"/></OBJECT_TYPES><LINK_TYPES><LINK_TYPE NAME=\"KNOWS\""
                        + " DIRECTED=\"true\"><SOURCE_TYPES><TYPE_REF"
                        + " NAME=\"PERSON\"/></SOURCE_TYPES><TARGET_TYPES><TYPE_REF"
                        + " NAME=\"PERSON\"/></TARGET_TYPES></LINK_TYPE></LINK_TYPES></DATA_MODEL>";
        var manager = new ModelManager();
        manager.loadModelFromXml(xml);
        try (var store = new Neo4jModelStore()) {
            store.createModel(manager.getCurrentModel(), xml);
        }
    }

    @After
    public void cleanup() {
        if (key != null)
            try (var store = new Neo4jModelStore()) {
                store.deleteModelAndDataByKey(key);
            }
    }

    private String definition(String version) {
        return "<WORKFLOW ID=\"review\" VERSION=\""
                + version
                + "\" LABEL=\"Review\" INITIAL_STATE=\"draft\"><OBJECT_TYPES><TYPE_REF"
                + " NAME=\"PERSON\" INCLUDE_SUBTYPES=\"true\"/></OBJECT_TYPES><STATES><STATE"
                + " CODE=\"draft\" LABEL=\"Draft\"/><STATE CODE=\"done\" LABEL=\"Done\""
                + " TERMINAL=\"true\"/></STATES><TRANSITIONS><TRANSITION ID=\"finish\""
                + " FROM=\"draft\" TO=\"done\" LABEL=\"Finish\"/></TRANSITIONS></WORKFLOW>";
    }

    private long object(boolean legacy) {
        try (var s = Neo4jDriverProvider.getDriver().session()) {
            return s.executeWrite(
                    tx -> {
                        var p = new HashMap<String, Object>();
                        p.put("modelKey", key);
                        p.put("type", "PERSON");
                        if (!legacy) p.put("uuid", UUID.randomUUID().toString());
                        return tx.run(
                                        "CREATE (n:DataObject) SET n=$p RETURN id(n) AS id",
                                        Map.of("p", p))
                                .single()
                                .get("id")
                                .asLong();
                    });
        }
    }

    private Map<String, Object> props(long id) {
        try (var s = Neo4jDriverProvider.getDriver().session()) {
            return s.executeRead(
                    tx ->
                            tx.run(
                                            "MATCH (n:DataObject {modelKey:$key}) WHERE id(n)=$id"
                                                    + " RETURN properties(n) AS p",
                                            Map.of("key", key, "id", id))
                                    .single()
                                    .get("p")
                                    .asMap());
        }
    }

    private WorkflowMigrationService.Request request(
            String version, String mode, List<Long> ids, Map<String, String> mapping, String hash) {
        return new WorkflowMigrationService.Request(
                key, "review", version, mode, List.of(), ids, null, null, mapping, hash);
    }

    @Test
    public void immutableUploadExplicitActivationAndDeletionProtection() {
        catalog.upload(key, definition("1"), actor);
        assertThrows(
                StorageConflictException.class, () -> catalog.upload(key, definition("1"), actor));
        var review = catalog.activate(key, "review", "1", null, false, actor);
        assertEquals(List.of("EMPLOYEE", "PERSON"), review.get("objectTypes"));
        assertEquals(List.of(), catalog.list(key).get("bindings"));
        catalog.activate(key, "review", "1", (String) review.get("previewHash"), true, actor);
        assertThrows(
                StorageConflictException.class, () -> catalog.delete(key, "review", "1", actor));
        catalog.deactivate(key, List.of("PERSON", "EMPLOYEE"), actor);
        catalog.delete(key, "review", "1", actor);
        assertEquals(List.of(), catalog.list(key).get("items"));
    }

    @Test
    public void activationHashBecomesStaleWhenCatalogChanges() {
        catalog.upload(key, definition("1"), actor);
        var p = catalog.activate(key, "review", "1", null, false, actor);
        catalog.upload(key, definition("2"), actor);
        assertThrows(
                StorageConflictException.class,
                () ->
                        catalog.activate(
                                key, "review", "1", (String) p.get("previewHash"), true, actor));
    }

    @Test
    public void initializesLegacyUuidOnlyOnExplicitCommitAndMigratesWithAudit() {
        catalog.upload(key, definition("1"), actor);
        long id = object(true);
        var service = new WorkflowMigrationService();
        var p =
                service.execute(
                        request("1", "initialize", List.of(id), Map.of(), null), false, actor);
        assertFalse(props(id).containsKey("uuid"));
        assertFalse(props(id).containsKey("_workflowState"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.execute(
                                request(
                                        "1",
                                        "initialize",
                                        List.of(),
                                        Map.of(),
                                        (String) p.get("previewHash")),
                                true,
                                actor));
        service.execute(
                request("1", "initialize", List.of(id), Map.of(), (String) p.get("previewHash")),
                true,
                actor);
        var before = props(id);
        assertEquals("draft", before.get("_workflowState"));
        assertEquals(1L, before.get("_workflowRevision"));
        assertNotNull(before.get("uuid"));
        catalog.upload(key, definition("2"), actor);
        var mapping = Map.of("draft", "done");
        var m = service.execute(request("2", "migrate", List.of(id), mapping, null), false, actor);
        service.execute(
                request("2", "migrate", List.of(id), mapping, (String) m.get("previewHash")),
                true,
                actor);
        var after = props(id);
        assertEquals(before.get("uuid"), after.get("uuid"));
        assertEquals("2", after.get("_workflowVersion"));
        assertEquals("done", after.get("_workflowState"));
        assertEquals(2L, after.get("_workflowRevision"));
        assertThrows(
                StorageConflictException.class,
                () ->
                        service.execute(
                                request(
                                        "2",
                                        "migrate",
                                        List.of(id),
                                        Map.of("done", "done"),
                                        (String) m.get("previewHash")),
                                true,
                                actor));
        assertThrows(
                StorageConflictException.class, () -> catalog.delete(key, "review", "2", actor));
        try (var s = Neo4jDriverProvider.getDriver().session()) {
            assertEquals(
                    2L,
                    (long)
                            s.executeRead(
                                    tx ->
                                            tx.run(
                                                            "MATCH (e:AuditData"
                                                                + " {modelKey:$key,entityType:'OBJECT'})"
                                                                + " WHERE e.action IN"
                                                                + " ['INITIALIZE','MIGRATE'] RETURN"
                                                                + " count(e) AS c",
                                                            Map.of("key", key))
                                                    .single()
                                                    .get("c")
                                                    .asLong()));
        }
    }

    @Test
    public void staleMigrationRevisionRejectsEntireBatch() {
        catalog.upload(key, definition("1"), actor);
        long first = object(false), second = object(false);
        var ids = List.of(first, second);
        var service = new WorkflowMigrationService();
        var p = service.execute(request("1", "initialize", ids, Map.of(), null), false, actor);
        try (var s = Neo4jDriverProvider.getDriver().session()) {
            s.executeWriteWithoutResult(
                    tx ->
                            tx.run(
                                            "MATCH (n:DataObject {modelKey:$key}) WHERE id(n)=$id"
                                                    + " SET n._workflowRevision=1",
                                            Map.of("key", key, "id", second))
                                    .consume());
        }
        assertThrows(
                StorageConflictException.class,
                () ->
                        service.execute(
                                request(
                                        "1",
                                        "initialize",
                                        ids,
                                        Map.of(),
                                        (String) p.get("previewHash")),
                                true,
                                actor));
        assertFalse(props(first).containsKey("_workflowState"));
    }

    @Test
    public void activeWorkflowBlocksIncompatibleInheritanceEdit() throws Exception {
        catalog.upload(key, definition("1"), actor);
        var p = catalog.activate(key, "review", "1", null, false, actor);
        catalog.activate(key, "review", "1", (String) p.get("previewHash"), true, actor);
        String changed = xml.replace(" PARENT=\"PERSON\"", "");
        var candidate = new ModelManager();
        candidate.loadModelFromXml(changed);
        try (var s = Neo4jDriverProvider.getDriver().session()) {
            assertThrows(
                    StorageConflictException.class,
                    () ->
                            s.executeRead(
                                    tx -> {
                                        WorkflowCatalog.validateModelUpdate(
                                                tx, key, candidate.getCurrentModel());
                                        return null;
                                    }));
        }
    }
}
