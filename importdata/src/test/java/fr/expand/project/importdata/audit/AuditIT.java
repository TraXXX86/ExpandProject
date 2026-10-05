package fr.expand.project.importdata.audit;

import static org.junit.Assert.*;

import fr.expand.project.importdata.access.AccessContext;
import fr.expand.project.importdata.api.impl.ModelBasedImportAPI;
import fr.expand.project.importdata.dao.Neo4jDriverProvider;
import fr.expand.project.importdata.data.Neo4jDataStore;
import fr.expand.project.importdata.dto.generated.*;
import fr.expand.project.importdata.model.*;

import org.junit.*;

import java.util.*;

public class AuditIT {
    private String key;
    private ModelManager manager;
    private Neo4jModelStore models;
    private AuditActor actor;

    @Before
    public void setup() throws Exception {
        String name = "AuditIT_" + UUID.randomUUID().toString().replace("-", "");
        key = name + ":1";
        manager = new ModelManager();
        manager.loadModelFromXml(
                """
<DATA_MODEL NAME="%s" VERSION="1"><OBJECT_TYPES><OBJECT_TYPE NAME="PERSON"><ATTRIBUTE_DEFINITIONS>
<ATTRIBUTE_DEFINITION NAME="NAME" TYPE="STRING" REQUIRED="true" SEARCHABLE="true"/>
</ATTRIBUTE_DEFINITIONS></OBJECT_TYPE></OBJECT_TYPES><LINK_TYPES><LINK_TYPE NAME="KNOWS" DIRECTED="true">
<SOURCE_TYPES><TYPE_REF NAME="PERSON"/></SOURCE_TYPES><TARGET_TYPES><TYPE_REF NAME="PERSON"/></TARGET_TYPES>
<ATTRIBUTE_DEFINITIONS><ATTRIBUTE_DEFINITION NAME="NOTE" TYPE="STRING"/></ATTRIBUTE_DEFINITIONS>
</LINK_TYPE></LINK_TYPES></DATA_MODEL>
"""
                        .formatted(name));
        actor =
                AuditActor.from(
                        new AccessContext(
                                "secret-token",
                                Map.of("username", "admin", "platformAdmin", true),
                                Map.of("username", "editor", "portalUser", true),
                                List.of(),
                                true,
                                1));
        models = new Neo4jModelStore(actor);
        models.createModel(manager.getCurrentModel(), manager.getCurrentModelXml());
    }

    @After
    public void cleanup() {
        if (models != null) models.deleteModelAndDataByKey(key);
        if (key != null)
            try (var session = Neo4jDriverProvider.getDriver().session()) {
                session.run("MATCH (a:AuditData {modelKey:$key}) DELETE a", Map.of("key", key))
                        .consume();
            }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> events(String type, String id, String action) {
        return (List<Map<String, Object>>)
                AuditTrail.list(key, 0, 100, type, id, action).get("items");
    }

    @Test
    public void snapshotsTrackImpersonationUpdatesAndCascadingDeletes() {
        try (var store = new Neo4jDataStore(manager.getCurrentModelXml(), actor)) {
            long first = store.createObject(key, "PERSON", Map.of("NAME", "Alice"), 0);
            long second = store.createObject(key, "PERSON", Map.of("NAME", "Bob"), 1);
            long relation =
                    store.createLink(key, first, second, "KNOWS", true, Map.of("NOTE", "before"));
            String uuid = (String) store.loadObjectById(key, first).get("uuid");
            String relUuid = (String) store.loadLinkById(key, relation).get("uuid");
            assertTrue(store.updateObject(key, first, Map.of("NAME", "Alicia")));
            assertTrue(store.updateLinkByUuid(key, relUuid, Map.of("NOTE", "after")));
            var changed = events("OBJECT", uuid, "UPDATE").get(0);
            assertEquals("admin", changed.get("actor"));
            assertEquals("editor", changed.get("effectiveUser"));
            assertEquals(actor.operationId(), changed.get("operationId"));
            assertEquals("Alice", ((Map<?, ?>) changed.get("before")).get("NAME"));
            assertEquals("Alicia", ((Map<?, ?>) changed.get("after")).get("NAME"));
            assertFalse(((Map<?, ?>) changed.get("after")).containsKey("searchText"));
            assertFalse(changed.toString().contains("secret-token"));
            assertEquals(
                    "after",
                    ((Map<?, ?>) events("LINK", relUuid, "UPDATE").get(0).get("after"))
                            .get("NOTE"));
            assertTrue(store.deleteObject(key, first));
            assertEquals(1, events("OBJECT", uuid, "DELETE").size());
            assertEquals(1, events("LINK", relUuid, "DELETE").size());
            assertNull(events("LINK", relUuid, "DELETE").get(0).get("after"));
        }
    }

    @Test
    public void failedBatchRollsBackObjectsAndAuditEvents() {
        DATAS data = pack(1, 1); // duplicate identity fails after the first CREATE+audit
        try (var connector =
                new fr.expand.project.importdata.dao.connectors.impl.CypherConnector(actor)) {
            try {
                connector.importData(data, key, manager);
                fail("Expected duplicate");
            } catch (fr.expand.project.importdata.dao.StorageConflictException expected) {
            }
        }
        try (var store = new Neo4jDataStore()) {
            assertTrue(store.loadObjects(key).isEmpty());
        }
        assertTrue(events("OBJECT", null, null).isEmpty());
        assertTrue(events("IMPORT", null, null).isEmpty());
    }

    @Test
    public void successfulImportSharesOperationAndDryRunWritesNoEvents() {
        try (var importer = new ModelBasedImportAPI(manager, actor)) {
            assertTrue(importer.importData(pack(1, 2), true, key).isValid());
            assertTrue(events("OBJECT", null, null).isEmpty());
            assertTrue(importer.importData(pack(1, 2), false, key).isValid());
        }
        assertEquals(2, events("OBJECT", null, "CREATE").size());
        var event = events("IMPORT", null, "IMPORT").get(0);
        assertEquals(actor.operationId(), event.get("operationId"));
        assertEquals(2L, ((Map<?, ?>) event.get("after")).get("objectCount"));
    }

    @Test
    public void modelHistorySurvivesDeletionAndIsPaginated() {
        models.updateModel(key, manager.getCurrentModel(), manager.getCurrentModelXml());
        models.deleteModelAndDataByKey(key);
        assertNull(models.loadModelXmlByKey(key));
        assertEquals(3, events("MODEL", key, null).size());
        var page = AuditTrail.list(key, 0, 1, "MODEL", key, null);
        assertEquals(3L, page.get("total"));
        assertEquals(true, page.get("hasMore"));
        assertEquals(1, ((List<?>) page.get("items")).size());
        assertEquals(1, events("MODEL", key, "DELETE").size());
    }

    private DATAS pack(int... ids) {
        DATAS data = new DATAS();
        data.setOBJECTS(new OBJECTS());
        data.setLINKS(new LINKS());
        for (int id : ids) {
            OBJECT object = new OBJECT();
            object.setID(id);
            object.setTYPE("PERSON");
            ATTRIBUTE name = new ATTRIBUTE();
            name.setKEY("NAME");
            name.setVALUE("Person " + id);
            object.getATTRIBUTE().add(name);
            data.getOBJECTS().getOBJECT().add(object);
        }
        return data;
    }
}
