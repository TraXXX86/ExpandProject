package fr.expand.project.importdata.imports;

import static org.junit.Assert.*;

import fr.expand.project.importdata.audit.AuditActor;
import fr.expand.project.importdata.dao.*;
import fr.expand.project.importdata.model.*;

import org.junit.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class ImportWorkflowIT {
    private String key;
    private ModelManager manager;
    private Neo4jModelStore models;
    private ImportWorkflowStore imports = new ImportWorkflowStore();

    @Before
    public void setup() throws Exception {
        String name = "ImportIT_" + UUID.randomUUID().toString().replace("-", "");
        key = name + ":1";
        manager = new ModelManager();
        manager.loadModelFromXml(
                """
<DATA_MODEL NAME="%s" VERSION="1"><OBJECT_TYPES><OBJECT_TYPE NAME="PERSON"><ATTRIBUTE_DEFINITIONS><ATTRIBUTE_DEFINITION NAME="NAME" TYPE="STRING" REQUIRED="true" SEARCHABLE="true"/></ATTRIBUTE_DEFINITIONS></OBJECT_TYPE></OBJECT_TYPES><LINK_TYPES><LINK_TYPE NAME="KNOWS" DIRECTED="true"><SOURCE_TYPES><TYPE_REF NAME="PERSON"/></SOURCE_TYPES><TARGET_TYPES><TYPE_REF NAME="PERSON"/></TARGET_TYPES><ATTRIBUTE_DEFINITIONS><ATTRIBUTE_DEFINITION NAME="NOTE" TYPE="STRING"/></ATTRIBUTE_DEFINITIONS></LINK_TYPE></LINK_TYPES></DATA_MODEL>
"""
                        .formatted(name));
        models = new Neo4jModelStore();
        models.createModel(manager.getCurrentModel(), manager.getCurrentModelXml());
    }

    @After
    public void cleanup() {
        if (models != null) models.deleteModelAndDataByKey(key);
    }

    private ImportInput csv(String text) {
        return ImportInput.table(
                TabularFile.csv(text.getBytes(StandardCharsets.UTF_8)),
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

    private long count() {
        try (var s = Neo4jDriverProvider.getDriver().session()) {
            return s.run(
                            "MATCH (n:DataObject {modelKey:$key}) RETURN count(n) AS n",
                            Map.of("key", key))
                    .single()
                    .get("n")
                    .asLong();
        }
    }

    @Test
    public void previewDoesNotWriteAndUpsertIsIdempotent() {
        var input = csv("id,name\n0,O'Brien\n1,Alice");
        var preview = imports.preview(key, manager, input, "create");
        assertEquals(0, count());
        assertEquals(Long.valueOf(2), preview.summary().get("additions"));
        imports.commit(
                key,
                manager,
                input,
                "create",
                preview.previewHash(),
                true,
                false,
                AuditActor.system());
        assertEquals(2, count());
        var unchanged = imports.preview(key, manager, input, "upsert");
        assertEquals(Long.valueOf(2), unchanged.summary().get("unchanged"));
        imports.commit(
                key,
                manager,
                input,
                "upsert",
                unchanged.previewHash(),
                true,
                false,
                AuditActor.system());
        assertEquals(2, count());
        assertFalse(imports.preview(key, manager, input, "create").valid());
    }

    @Test
    public void stalePreviewAndInsufficientUpdatePermissionLeaveEverythingUntouched() {
        commit(csv("id,name\n0,Alice"), "create");
        var desired = csv("id,name\n0,Updated\n1,New");
        var preview = imports.preview(key, manager, desired, "upsert");
        assertThrows(
                io.javalin.http.ForbiddenResponse.class,
                () ->
                        imports.commit(
                                key,
                                manager,
                                desired,
                                "upsert",
                                preview.previewHash(),
                                true,
                                false,
                                AuditActor.system()));
        assertEquals(1, count());
        commit(csv("id,name\n0,Concurrent"), "upsert");
        assertThrows(
                StorageConflictException.class,
                () ->
                        imports.commit(
                                key,
                                manager,
                                desired,
                                "upsert",
                                preview.previewHash(),
                                true,
                                true,
                                AuditActor.system()));
        assertEquals(1, count());
    }

    @Test
    public void lateValidationConflictRollsBackWholeFile() {
        var input = csv("id,name\n0,Alice\n1,");
        var preview = imports.preview(key, manager, input, "create");
        assertFalse(preview.valid());
        assertEquals(Long.valueOf(1), preview.summary().get("conflicts"));
        assertEquals(3, preview.rows().get(1).row());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        imports.commit(
                                key,
                                manager,
                                input,
                                "create",
                                preview.previewHash(),
                                true,
                                true,
                                AuditActor.system()));
        assertEquals(0, count());
    }

    @Test
    public void xmlRelationshipsUpsertAtomicallyAndCannotDuplicate() throws Exception {
        var input =
                ImportInput.xml(
                        "<DATAS><OBJECTS><OBJECT TYPE=\"PERSON\" ID=\"0\"><ATTRIBUTE KEY=\"NAME\""
                            + " VALUE=\"Alice\"/></OBJECT><OBJECT TYPE=\"PERSON\""
                            + " ID=\"1\"><ATTRIBUTE KEY=\"NAME\""
                            + " VALUE=\"Bob\"/></OBJECT></OBJECTS><LINKS><LINK"
                            + " TYPE=\"KNOWS\"><ATTRIBUTE KEY=\"NOTE\""
                            + " VALUE=\"friend\"/><OBJ_LINK_A TYPE=\"PERSON\" ID=\"0\"/><OBJ_LINK_B"
                            + " TYPE=\"PERSON\" ID=\"1\"/></LINK></LINKS></DATAS>");
        commit(input, "upsert");
        assertEquals(2, count());
        var second = imports.preview(key, manager, input, "upsert");
        assertEquals(Long.valueOf(3), second.summary().get("unchanged"));
        imports.commit(
                key,
                manager,
                input,
                "upsert",
                second.previewHash(),
                true,
                false,
                AuditActor.system());
        try (var s = Neo4jDriverProvider.getDriver().session()) {
            assertEquals(
                    1,
                    s.run(
                                    "MATCH (:DataObject {modelKey:$key})-[r:KNOWS]->(:DataObject"
                                            + " {modelKey:$key}) RETURN count(r) AS n",
                                    Map.of("key", key))
                            .single()
                            .get("n")
                            .asLong());
        }
    }

    @Test
    public void schemaChangesInvalidatePreview() throws Exception {
        var input = csv("id,name\n0,Alice");
        var preview = imports.preview(key, manager, input, "create");
        var changed = new ModelManager();
        changed.loadModelFromXml(
                manager.getCurrentModelXml()
                        .replace("SEARCHABLE=\"true\"", "SEARCHABLE=\"false\""));
        models.updateModel(key, changed.getCurrentModel(), changed.getCurrentModelXml());
        assertThrows(
                StorageConflictException.class,
                () ->
                        imports.commit(
                                key,
                                manager,
                                input,
                                "create",
                                preview.previewHash(),
                                true,
                                true,
                                AuditActor.system()));
        assertEquals(0, count());
    }

    @Test
    public void reversedUndirectedLegacyLinkReceivesUuidAndUpdatesOnce() throws Exception {
        var changed = new ModelManager();
        changed.loadModelFromXml(
                manager.getCurrentModelXml().replace("DIRECTED=\"true\"", "DIRECTED=\"false\""));
        models.updateModel(key, changed.getCurrentModel(), changed.getCurrentModelXml());
        manager = changed;
        commit(csv("id,name\n0,Alice\n1,Bob"), "create");
        try (var s = Neo4jDriverProvider.getDriver().session()) {
            s.run(
                            "MATCH (a:DataObject {modelKey:$key,dataId:0}),(b:DataObject"
                                    + " {modelKey:$key,dataId:1}) CREATE (b)-[:KNOWS"
                                    + " {NOTE:'old',modelKey:$key,directed:false}]->(a)",
                            Map.of("key", key))
                    .consume();
        }
        var input =
                ImportInput.xml(
                        "<DATAS><OBJECTS/><LINKS><LINK TYPE=\"KNOWS\"><ATTRIBUTE KEY=\"NOTE\""
                            + " VALUE=\"new\"/><OBJ_LINK_A TYPE=\"PERSON\" ID=\"0\"/><OBJ_LINK_B"
                            + " TYPE=\"PERSON\" ID=\"1\"/></LINK></LINKS></DATAS>");
        var preview = imports.preview(key, manager, input, "upsert");
        assertEquals(Long.valueOf(1), preview.summary().get("updates"));
        imports.commit(
                key,
                manager,
                input,
                "upsert",
                preview.previewHash(),
                true,
                true,
                AuditActor.system());
        try (var s = Neo4jDriverProvider.getDriver().session()) {
            var links =
                    s.run(
                                    "MATCH (:DataObject {modelKey:$key})-[r:KNOWS]->(:DataObject"
                                        + " {modelKey:$key}) RETURN r.NOTE AS note,r.uuid AS uuid",
                                    Map.of("key", key))
                            .list();
            assertEquals(1, links.size());
            assertEquals("new", links.get(0).get("note").asString());
            assertFalse(links.get(0).get("uuid").isNull());
        }
    }
}
