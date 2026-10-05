package fr.expand.project.importdata.storage;

import fr.expand.project.importdata.api.impl.ModelBasedImportAPI;
import fr.expand.project.importdata.dao.Neo4jDriverProvider;
import fr.expand.project.importdata.dao.connectors.impl.CypherConnector;
import fr.expand.project.importdata.data.Neo4jDataStore;
import fr.expand.project.importdata.dto.*;
import fr.expand.project.importdata.dto.generated.*;
import fr.expand.project.importdata.dto.util.DataPackDtoUtils;
import fr.expand.project.importdata.model.*;
import fr.expand.project.importdata.util.CypherUtils;

import org.junit.*;
import org.neo4j.driver.Session;

import java.util.*;

/** Explicit integration suite; uses only uniquely named model subgraphs and cleans them up. */
public class StorageIT {
    private String name, key;
    private ModelManager manager;
    private Neo4jModelStore models;
    private Neo4jDataStore store;

    @Before
    public void setup() throws Exception {
        name = "StorageIT_" + UUID.randomUUID().toString().replace("-", "");
        key = name + ":1";
        manager = new ModelManager();
        manager.loadModelFromXml(xml(true));
        models = new Neo4jModelStore();
        models.createModel(manager.getCurrentModel(), manager.getCurrentModelXml());
        store = new Neo4jDataStore();
    }

    @After
    public void cleanup() {
        if (models != null) models.deleteModelAndDataByKey(key);
    }

    private String xml(boolean oldField) {
        return """
<DATA_MODEL NAME="%s" VERSION="1"><OBJECT_TYPES><OBJECT_TYPE NAME="PERSON"><ATTRIBUTE_DEFINITIONS>
<ATTRIBUTE_DEFINITION NAME="NAME" TYPE="STRING" REQUIRED="true" SEARCHABLE="true"/>
%s
</ATTRIBUTE_DEFINITIONS></OBJECT_TYPE></OBJECT_TYPES><LINK_TYPES><LINK_TYPE NAME="KNOWS" DIRECTED="false">
<SOURCE_TYPES><TYPE_REF NAME="PERSON"/></SOURCE_TYPES><TARGET_TYPES><TYPE_REF NAME="PERSON"/></TARGET_TYPES>
<ATTRIBUTE_DEFINITIONS><ATTRIBUTE_DEFINITION NAME="NOTE" TYPE="STRING"/></ATTRIBUTE_DEFINITIONS>
</LINK_TYPE></LINK_TYPES></DATA_MODEL>
"""
                .formatted(
                        name,
                        oldField
                                ? "<ATTRIBUTE_DEFINITION NAME=\"OBSOLETE\" TYPE=\"STRING\"/>"
                                : "");
    }

    private DataPackObject person(int id, String value) {
        var person = new DataPackObject();
        person.setID(id);
        person.setTYPE("PERSON");
        person.getATTRIBUTE().add(new DataPackAttribute("NAME", value));
        return person;
    }

    private DATAS pack(DataPackObject... objects) {
        var data = new DATAS();
        data.setOBJECTS(new OBJECTS());
        data.setLINKS(new LINKS());
        data.getOBJECTS().getOBJECT().addAll(Arrays.asList(objects));
        return data;
    }

    private void addLink(DATAS data, DataPackObject a, DataPackObject b, String value) {
        var link = new LINK();
        link.setTYPE("KNOWS");
        link.setOBJLINKA(DataPackDtoUtils.createObjLink(a));
        link.setOBJLINKB(DataPackDtoUtils.createObjLink(b));
        link.getATTRIBUTE().add(new DataPackAttribute("NOTE", value));
        data.getLINKS().getLINK().add(link);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(Map<String, Object> page, String key) {
        return (List<Map<String, Object>>) page.get(key);
    }

    @SuppressWarnings("unchecked")
    private String attribute(Map<String, Object> row, String key) {
        return ((List<Map<String, Object>>) row.get("attributes"))
                .stream()
                        .filter(a -> key.equals(a.get("key")))
                        .map(a -> String.valueOf(a.get("value")))
                        .findFirst()
                        .orElse(null);
    }

    @Test
    public void valuesAndRelationshipAttributesRemainLiteral() {
        String literal = "O'Brien \\ \"quoted\" ') DETACH DELETE n //";
        var a = person(0, literal);
        var b = person(1, "Second");
        var data = pack(a, b);
        addLink(data, a, b, literal);
        try (var api = new ModelBasedImportAPI(manager)) {
            Assert.assertTrue(api.importData(data, false, key).isValid());
        }
        var objects = store.loadObjects(key);
        Assert.assertEquals(2, objects.size());
        var zeroIdObject =
                objects.stream()
                        .filter(object -> ((Number) object.get("dataId")).intValue() == 0)
                        .findFirst()
                        .orElseThrow();
        Assert.assertEquals(literal, attribute(zeroIdObject, "NAME"));
        Assert.assertEquals(0, ((Number) zeroIdObject.get("dataId")).intValue());
        var link = store.loadLinks(key).get(0);
        Assert.assertEquals(literal, attribute(link, "NOTE"));
        Assert.assertEquals(false, link.get("directed"));
        long linkId = ((Number) link.get("id")).longValue();
        Assert.assertTrue(store.updateLink(key, linkId, Map.of("NOTE", "new ' literal")));
        Assert.assertEquals("new ' literal", attribute(store.loadLinkById(key, linkId), "NOTE"));
        Assert.assertTrue(store.deleteLink(key, linkId));
        Assert.assertNull(store.loadLinkById(key, linkId));
    }

    @Test
    public void importRollsBackOnLateFailureAndDuplicateIdentity() {
        var a = person(1, "First");
        var invalid = person(2, "Bad");
        invalid.getATTRIBUTE().add(new DataPackAttribute("modelKey", "escape"));
        try (var connector = new CypherConnector()) {
            Assert.assertThrows(
                    IllegalArgumentException.class,
                    () -> connector.importData(pack(a, invalid), key, manager));
        }
        Assert.assertTrue(store.loadObjects(key).isEmpty());
        try (var connector = new CypherConnector()) {
            connector.importData(pack(person(0, "Existing")), key, manager);
            Assert.assertThrows(
                    fr.expand.project.importdata.dao.StorageConflictException.class,
                    () ->
                            connector.importData(
                                    pack(person(8, "new"), person(0, "duplicate")), key, manager));
        }
        Assert.assertEquals(1, store.loadObjects(key).size());
    }

    @Test
    public void schemaReplacementRemovesOrphanAttributesAndRejectsCollision() throws Exception {
        Assert.assertThrows(
                fr.expand.project.importdata.dao.StorageConflictException.class,
                () -> models.createModel(manager.getCurrentModel(), manager.getCurrentModelXml()));
        manager.loadModelFromXml(xml(false));
        models.updateModel(key, manager.getCurrentModel(), manager.getCurrentModelXml());
        models.updateModel(key, manager.getCurrentModel(), manager.getCurrentModelXml());
        try (Session session = Neo4jDriverProvider.getDriver().session()) {
            long count =
                    session.run(
                                    "MATCH (a:ModelAttribute {modelKey:$key}) RETURN count(a) AS"
                                            + " count",
                                    Map.of("key", key))
                            .single()
                            .get("count")
                            .asLong();
            Assert.assertEquals(2, count);
            long orphan =
                    session.run(
                                    "MATCH (a:ModelAttribute {modelKey:$key}) WHERE NOT"
                                            + " ()-[:HAS_ATTRIBUTE]->(a) RETURN count(a) AS count",
                                    Map.of("key", key))
                            .single()
                            .get("count")
                            .asLong();
            Assert.assertEquals(0, orphan);
        }
        var changed = new ModelManager();
        changed.loadModelFromXml(xml(false).replace("VERSION=\"1\"", "VERSION=\"2\""));
        Assert.assertThrows(
                IllegalArgumentException.class,
                () ->
                        models.updateModel(
                                key, changed.getCurrentModel(), changed.getCurrentModelXml()));
    }

    @Test
    public void staleValidatedSchemaCannotImport() throws Exception {
        var updated = new ModelManager();
        updated.loadModelFromXml(xml(false));
        models.updateModel(key, updated.getCurrentModel(), updated.getCurrentModelXml());
        try (var connector = new CypherConnector()) {
            Assert.assertThrows(
                    fr.expand.project.importdata.dao.StorageConflictException.class,
                    () -> connector.importData(pack(person(1, "stale")), key, manager));
        }
        Assert.assertTrue(store.loadObjects(key).isEmpty());
    }

    @Test
    public void staleSchemaSnapshotRejectsCrudAndMissingModelsRejectWrites() throws Exception {
        long id = store.createObject(key, "PERSON", Map.of("NAME", "original"), 0);
        try (var snapshot = new Neo4jDataStore(manager.getCurrentModelXml())) {
            var updated = new ModelManager();
            updated.loadModelFromXml(xml(false));
            models.updateModel(key, updated.getCurrentModel(), updated.getCurrentModelXml());
            Assert.assertThrows(
                    fr.expand.project.importdata.dao.StorageConflictException.class,
                    () -> snapshot.createObject(key, "PERSON", Map.of("NAME", "new")));
            Assert.assertThrows(
                    fr.expand.project.importdata.dao.StorageConflictException.class,
                    () -> snapshot.updateObject(key, id, Map.of("NAME", "changed")));
            Assert.assertThrows(
                    fr.expand.project.importdata.dao.StorageConflictException.class,
                    () -> snapshot.createLink(key, id, id, "KNOWS", false, Map.of()));
        }
        Assert.assertEquals("original", attribute(store.loadObjectById(key, id), "NAME"));
        Assert.assertThrows(
                IllegalArgumentException.class,
                () -> store.createObject("missing:1", "PERSON", Map.of("NAME", "orphan")));
    }

    @Test
    public void relationshipUuidSupportsStableScopedMutations() throws Exception {
        long a = store.createObject(key, "PERSON", Map.of("NAME", "A"));
        long b = store.createObject(key, "PERSON", Map.of("NAME", "B"));
        long numeric = store.createLink(key, a, b, "KNOWS", false, Map.of("NOTE", "original"));
        String uuid = String.valueOf(store.loadLinkById(key, numeric).get("uuid"));
        Assert.assertNotNull(store.loadLinkByUuid(key, uuid));
        Assert.assertTrue(store.updateLinkByUuid(key, uuid, Map.of("NOTE", "updated")));
        Assert.assertEquals("updated", attribute(store.loadLinkByUuid(key, uuid), "NOTE"));
        Assert.assertEquals(uuid, store.loadLinkById(key, numeric).get("uuid"));
        String siblingName = name + "_sibling";
        var sibling = new ModelManager();
        sibling.loadModelFromXml(
                xml(false).replace("NAME=\"" + name + "\"", "NAME=\"" + siblingName + "\""));
        String siblingKey =
                models.createModel(sibling.getCurrentModel(), sibling.getCurrentModelXml());
        try {
            Assert.assertNull(store.loadLinkByUuid(siblingKey, uuid));
            Assert.assertFalse(
                    store.updateLinkByUuid(siblingKey, uuid, Map.of("NOTE", "forbidden")));
            Assert.assertFalse(store.deleteLinkByUuid(siblingKey, uuid));
        } finally {
            models.deleteModelAndDataByKey(siblingKey);
        }
        Assert.assertThrows(
                IllegalArgumentException.class,
                () ->
                        store.updateLinkByUuid(
                                key, uuid, Map.of("uuid", UUID.randomUUID().toString())));
        Assert.assertTrue(store.deleteLinkByUuid(key, uuid));
        Assert.assertNull(store.loadLinkByUuid(key, uuid));
        Assert.assertFalse(store.deleteLinkByUuid(key, uuid));
        long replacement = store.createLink(key, a, b, "KNOWS", true, Map.of());
        Assert.assertNotEquals(uuid, store.loadLinkById(key, replacement).get("uuid"));
        Assert.assertFalse(store.updateLinkByUuid(key, uuid, Map.of("NOTE", "stale")));
        Assert.assertNotNull(store.loadLinkById(key, replacement));
    }

    @Test
    public void nativeFulltextIndexTracksWritesAndTreatsQueryOperatorsLiterally() {
        long first =
                store.createObject(
                        key,
                        "PERSON",
                        Map.of("NAME", "Alpha O'Brien \"quoted\"", "OBSOLETE", "private-secret"));
        store.createObject(key, "PERSON", Map.of("NAME", "Zulu"));
        var initial = store.loadDataPage(key, 0, 20, "ALPHA", "PERSON", "fulltext");
        Assert.assertEquals(1L, initial.get("totalObjects"));
        Assert.assertNull(attribute(rows(initial, "objects").get(0), "searchText"));
        Assert.assertEquals(
                0L,
                store.loadDataPage(key, 0, 20, "private-secret", "PERSON", "fulltext")
                        .get("totalObjects"));
        Assert.assertEquals(
                1L,
                store.loadDataPage(key, 0, 20, "\"quoted\"", "PERSON", "fulltext")
                        .get("totalObjects"));
        Assert.assertEquals(
                0L,
                store.loadDataPage(key, 0, 20, "Alpha OR Zulu", "PERSON", "fulltext")
                        .get("totalObjects"));
        Assert.assertEquals(
                0L,
                store.loadDataPage(key, 0, 20, "name:Alpha OR *:*", "PERSON", "fulltext")
                        .get("totalObjects"));
        Assert.assertThrows(
                IllegalArgumentException.class,
                () -> store.updateObject(key, first, Map.of("searchText", "private-secret")));
        store.updateObject(key, first, Map.of("NAME", "Changed"));
        Assert.assertEquals(
                0L,
                store.loadDataPage(key, 0, 20, "ALPHA", "PERSON", "fulltext").get("totalObjects"));
        Assert.assertEquals(
                1L,
                store.loadDataPage(key, 0, 20, "Changed", "PERSON", "fulltext")
                        .get("totalObjects"));
        store.deleteObject(key, first);
        Assert.assertEquals(
                0L,
                store.loadDataPage(key, 0, 20, "Changed", "PERSON", "fulltext")
                        .get("totalObjects"));
    }

    @Test
    public void fulltextBackfillsLegacyNodesAndFiltersOtherModelsBeforeCounting() throws Exception {
        String sibling = key + "_sibling";
        try (Session session = Neo4jDriverProvider.getDriver().session()) {
            session.run(
                            "MATCH (m:DataModel {key:$key}) REMOVE m.searchRevision",
                            Map.of("key", key))
                    .consume();
            session.run(
                            "CREATE (n:DataObject:PERSON {modelKey:$key,NAME:'Legacy"
                                    + " Alpha',OBSOLETE:'private-secret'})",
                            Map.of("key", key))
                    .consume();
            session.run(
                            "CREATE (n:DataObject:PERSON"
                                + " {modelKey:$key,type:'PERSON',searchText:'Alpha other tenant'})",
                            Map.of("key", sibling))
                    .consume();
        }
        try {
            var page = store.loadDataPage(key, 0, 1, "Alpha", "PERSON", "fulltext");
            Assert.assertEquals(1L, page.get("totalObjects"));
            Assert.assertEquals(1, rows(page, "objects").size());
            Assert.assertEquals(false, page.get("hasMore"));
            Assert.assertEquals("Legacy Alpha", attribute(rows(page, "objects").get(0), "NAME"));
            Assert.assertEquals(
                    0L,
                    store.loadDataPage(key, 0, 1, "private-secret", "PERSON", "fulltext")
                            .get("totalObjects"));
            manager.loadModelFromXml(
                    xml(true).replace("SEARCHABLE=\"true\"", "SEARCHABLE=\"false\""));
            models.updateModel(key, manager.getCurrentModel(), manager.getCurrentModelXml());
            Assert.assertEquals(
                    0L,
                    store.loadDataPage(key, 0, 1, "Alpha", "PERSON", "fulltext")
                            .get("totalObjects"));
        } finally {
            models.deleteModelAndDataByKey(sibling);
        }
    }

    @Test
    public void paginationSearchAndNeighborhoodStayWithinModel() {
        var a = person(0, "Alpha");
        var b = person(1, "Alpha child");
        var c = person(2, "Zulu");
        var data = pack(a, b, c);
        addLink(data, a, b, "first");
        addLink(data, a, c, "second");
        try (var api = new ModelBasedImportAPI(manager)) {
            Assert.assertTrue(api.importData(data, false, key).isValid());
        }
        var page = store.loadDataPage(key, 0, 1, "", "PERSON,OTHER");
        Assert.assertEquals(1, rows(page, "objects").size());
        Assert.assertEquals(3L, page.get("totalObjects"));
        Assert.assertEquals(true, page.get("hasMore"));
        Assert.assertTrue(rows(page, "links").isEmpty());
        long center =
                store.loadObjects(key).stream()
                        .filter(object -> ((Number) object.get("dataId")).intValue() == 0)
                        .mapToLong(object -> ((Number) object.get("id")).longValue())
                        .findFirst()
                        .orElseThrow();
        var neighbor = store.neighbors(key, center, 1);
        Assert.assertEquals(2, rows(neighbor, "objects").size());
        Assert.assertEquals(1, rows(neighbor, "links").size());
        Assert.assertEquals(true, neighbor.get("hasMore"));
        var search = store.loadDataPage(key, 0, 20, "ALPHA", "PERSON");
        Assert.assertEquals(2L, search.get("totalObjects"));
        Assert.assertEquals(1, rows(search, "links").size());
        Assert.assertNull(store.loadObjectById("other:1", center));
        Assert.assertThrows(
                IllegalArgumentException.class,
                () -> store.updateObject(key, center, Map.of("uuid", "overwrite")));
        Assert.assertThrows(
                IllegalArgumentException.class,
                () -> store.updateObject("other:1", center, Map.of()));
        Assert.assertThrows(
                IllegalArgumentException.class,
                () -> CypherUtils.identifier("PERSON`) DELETE n //"));
    }
}
