package fr.expand.project.importdata.api.server;

import static org.junit.Assert.*;

import com.google.gson.*;

import fr.expand.project.importdata.access.AccessControlStore;
import fr.expand.project.importdata.audit.AuditActor;
import fr.expand.project.importdata.dao.Neo4jDriverProvider;
import fr.expand.project.importdata.dao.StorageConflictException;
import fr.expand.project.importdata.workflow.WorkflowEngine;

import io.javalin.Javalin;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;

public class WorkflowObjectIT {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private final Map<String, String> settings = new HashMap<>();
    private String key, uuid, admin, reader, operator, base;
    private long objectId;
    private Javalin app;
    private static final Gson JSON = new Gson();
    private static final String XML =
            """
<WORKFLOW ID="review" VERSION="1" LABEL="Review" INITIAL_STATE="draft">
<OBJECT_TYPES><TYPE_REF NAME="PERSON"/></OBJECT_TYPES>
<STATES><STATE CODE="draft" LABEL="Draft"/><STATE CODE="review" LABEL="Review"/><STATE CODE="done" LABEL="Done" TERMINAL="true"/></STATES>
<TRANSITIONS><TRANSITION ID="submit" FROM="draft" TO="review" LABEL="Submit"/><TRANSITION ID="revise" FROM="review" TO="draft" LABEL="Revise"/><TRANSITION ID="approve" FROM="review" TO="done" LABEL="Approve"/></TRANSITIONS>
</WORKFLOW>
""";

    private void setting(String name, String value) {
        settings.put(name, System.getProperty(name));
        System.setProperty(name, value);
    }

    @Before
    public void setup() throws Exception {
        setting("ACCESS_DB_PATH", temp.newFolder().toPath().resolve("workflows.sqlite").toString());
        setting("EXPAND_ADMIN_PASSWORD", UUID.randomUUID().toString());
        String modelName = "WorkflowObjectIT_" + UUID.randomUUID().toString().replace("-", "");
        key = modelName + ":1";
        String modelXml =
                "<DATA_MODEL NAME=\""
                        + modelName
                        + "\" VERSION=\"1\"><OBJECT_TYPES><OBJECT_TYPE"
                        + " NAME=\"PERSON\"><ATTRIBUTE_DEFINITIONS><ATTRIBUTE_DEFINITION"
                        + " NAME=\"NAME\""
                        + " TYPE=\"STRING\"/></ATTRIBUTE_DEFINITIONS></OBJECT_TYPE></OBJECT_TYPES><LINK_TYPES/></DATA_MODEL>";
        uuid = UUID.randomUUID().toString();
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            objectId =
                    session.executeWrite(
                            tx -> {
                                tx.run(
                                                "CREATE (:DataModel {key:$key,xml:$modelXml})"
                                                    + " CREATE (:WorkflowDefinition"
                                                    + " {modelKey:$key,id:'review',version:'1',xml:$xml})"
                                                    + " CREATE (:WorkflowBinding"
                                                    + " {modelKey:$key,objectType:'PERSON',workflowId:'review',workflowVersion:'1'})",
                                                Map.of(
                                                        "key",
                                                        key,
                                                        "xml",
                                                        XML,
                                                        "modelXml",
                                                        modelXml))
                                        .consume();
                                Map<String, Object> props =
                                        new HashMap<>(
                                                WorkflowEngine.initialProperties(
                                                        tx, key, "PERSON"));
                                props.put("modelKey", key);
                                props.put("type", "PERSON");
                                props.put("uuid", uuid);
                                return tx.run(
                                                "CREATE (n:DataObject:PERSON) SET n=$props RETURN"
                                                        + " id(n) AS id",
                                                Map.of("props", props))
                                        .single()
                                        .get("id")
                                        .asLong();
                            });
        }
        try (var access = new AccessControlStore()) {
            for (String user : List.of("reader", "operator")) {
                access.upsertUser(user, user, true, false, false, UUID.randomUUID().toString());
                access.replaceModelPermissions(
                        user,
                        List.of(
                                Map.of(
                                        "modelKey",
                                        key,
                                        "visible",
                                        true,
                                        "canRead",
                                        true,
                                        "canTransition",
                                        user.equals("operator"))));
            }
            admin = (String) access.createSession("admin").get("token");
            reader = (String) access.createSession("reader").get("token");
            operator = (String) access.createSession("operator").get("token");
        }
        app = ImportApiServer.create().start(0);
        base = "http://127.0.0.1:" + app.port();
    }

    @After
    public void cleanup() {
        try {
            if (app != null) app.stop();
            if (key != null)
                try (var session = Neo4jDriverProvider.getDriver().session()) {
                    session.run(
                                    "MATCH (n) WHERE n.modelKey=$key OR (n:DataModel AND"
                                            + " n.key=$key) DETACH DELETE n",
                                    Map.of("key", key))
                            .consume();
                }
        } finally {
            settings.forEach(
                    (k, v) -> {
                        if (v == null) System.clearProperty(k);
                        else System.setProperty(k, v);
                    });
        }
    }

    private HttpResponse<String> request(String method, String path, String token, String body)
            throws Exception {
        var builder =
                HttpRequest.newBuilder(URI.create(base + path))
                        .header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return HttpClient.newHttpClient()
                .send(
                        builder.method(
                                        method,
                                        body == null
                                                ? HttpRequest.BodyPublishers.noBody()
                                                : HttpRequest.BodyPublishers.ofString(body))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
    }

    private String path() {
        return "/api/objects/" + objectId + "/workflow";
    }

    private HttpResponse<String> move(String token, String action, long revision) throws Exception {
        return request(
                "POST",
                path() + "/transitions",
                token,
                JSON.toJson(
                        Map.of(
                                "modelKey",
                                key,
                                "objectUuid",
                                uuid,
                                "transitionId",
                                action,
                                "expectedRevision",
                                revision)));
    }

    private long events() {
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            return session.run(
                            "MATCH (e:AuditData {modelKey:$key,action:'TRANSITION'}) RETURN"
                                    + " count(e) AS n",
                            Map.of("key", key))
                    .single()
                    .get("n")
                    .asLong();
        }
    }

    @Test
    public void cyclesRejectStaleAbaAndTerminalActionsAndAuditOnlyCommittedTransitions()
            throws Exception {
        assertEquals(400, move(operator, "approve", 0).statusCode());
        assertEquals(0, events());
        assertEquals(200, move(operator, "submit", 0).statusCode());
        assertEquals(409, move(operator, "submit", 0).statusCode());
        assertEquals(200, move(operator, "revise", 1).statusCode());
        assertEquals(409, move(operator, "submit", 0).statusCode());
        assertEquals(200, move(operator, "submit", 2).statusCode());
        var approved = move(operator, "approve", 3);
        assertEquals(200, approved.statusCode());
        var json = JsonParser.parseString(approved.body()).getAsJsonObject();
        assertTrue(json.getAsJsonObject("workflow").get("terminal").getAsBoolean());
        assertEquals(0, json.getAsJsonArray("transitions").size());
        assertEquals(400, move(operator, "revise", 4).statusCode());
        assertEquals(4, events());
    }

    @Test
    public void effectivePermissionsConstrainActionsAndImpersonationRecordsBothIdentities()
            throws Exception {
        var view = request("GET", path() + "?modelKey=" + key, reader, null);
        assertEquals(200, view.statusCode());
        assertEquals(
                0,
                JsonParser.parseString(view.body())
                        .getAsJsonObject()
                        .getAsJsonArray("transitions")
                        .size());
        assertEquals(403, move(reader, "submit", 0).statusCode());
        assertEquals(401, move(null, "submit", 0).statusCode());
        assertEquals(
                200,
                request("POST", "/api/auth/impersonate", admin, "{\"username\":\"reader\"}")
                        .statusCode());
        assertEquals(403, move(admin, "submit", 0).statusCode());
        assertEquals(
                200,
                request("POST", "/api/auth/impersonate", admin, "{\"username\":\"operator\"}")
                        .statusCode());
        assertEquals(200, move(admin, "submit", 0).statusCode());
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            var row =
                    session.run(
                                    "MATCH (e:AuditData {modelKey:$key,action:'TRANSITION'}) RETURN"
                                        + " e.actor AS actor,e.effectiveUser AS"
                                        + " effective,e.beforeJson AS before,e.afterJson AS after",
                                    Map.of("key", key))
                            .single();
            assertEquals("admin", row.get("actor").asString());
            assertEquals("operator", row.get("effective").asString());
            assertEquals(
                    "draft",
                    JsonParser.parseString(row.get("before").asString())
                            .getAsJsonObject()
                            .get("_workflowState")
                            .getAsString());
            assertEquals(
                    "review",
                    JsonParser.parseString(row.get("after").asString())
                            .getAsJsonObject()
                            .get("_workflowState")
                            .getAsString());
        }
    }

    @Test
    public void identityModelAndRevisionAreMandatoryAndReadsLeaveLegacyObjectsUnassigned()
            throws Exception {
        assertEquals(
                400,
                request(
                                "POST",
                                path() + "/transitions",
                                operator,
                                JSON.toJson(
                                        Map.of(
                                                "modelKey",
                                                key,
                                                "transitionId",
                                                "submit",
                                                "objectUuid",
                                                uuid)))
                        .statusCode());
        assertEquals(
                409,
                request(
                                "POST",
                                path() + "/transitions",
                                operator,
                                JSON.toJson(
                                        Map.of(
                                                "modelKey",
                                                key,
                                                "transitionId",
                                                "submit",
                                                "objectUuid",
                                                UUID.randomUUID().toString(),
                                                "expectedRevision",
                                                0)))
                        .statusCode());
        assertEquals(404, request("GET", path() + "?modelKey=other", admin, null).statusCode());
        assertEquals(0, events());
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            session.run(
                            "MATCH (n:DataObject {modelKey:$key}) REMOVE"
                                + " n._workflowId,n._workflowVersion,n._workflowState,n._workflowRevision,n._workflowUpdatedAt,n.uuid",
                            Map.of("key", key))
                    .consume();
        }
        var engine = new WorkflowEngine();
        var result = engine.loadObjectWorkflow(key, objectId, true);
        assertNull(result.get("workflow"));
        assertNull(result.get("objectUuid"));
        assertNull(engine.loadObjectWorkflow(key, objectId, true).get("workflow"));
        try (var session = Neo4jDriverProvider.getDriver().session()) {
            assertTrue(
                    session.run(
                                    "MATCH (n:DataObject {modelKey:$key}) RETURN n._workflowId IS"
                                            + " NULL AND n.uuid IS NULL AS unchanged",
                                    Map.of("key", key))
                            .single()
                            .get("unchanged")
                            .asBoolean());
        }
    }

    @Test
    public void concurrentDoubleClickCommitsExactlyOneTransitionAndOneAuditEvent()
            throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            Callable<Boolean> operation =
                    () -> {
                        start.await();
                        try {
                            new WorkflowEngine()
                                    .transition(
                                            key, objectId, uuid, "submit", 0, AuditActor.system());
                            return true;
                        } catch (StorageConflictException expected) {
                            return false;
                        }
                    };
            var first = pool.submit(operation);
            var second = pool.submit(operation);
            start.countDown();
            assertNotEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(1, events());
        } finally {
            pool.shutdownNow();
        }
    }

    private String user(
            String name,
            boolean portalUser,
            boolean modelAdmin,
            boolean visible,
            boolean read,
            boolean update) {
        try (var store = new AccessControlStore()) {
            store.upsertUser(
                    name, name, portalUser, modelAdmin, false, UUID.randomUUID().toString());
            store.replaceModelPermissions(
                    name,
                    List.of(
                            Map.of(
                                    "modelKey",
                                    key,
                                    "visible",
                                    visible,
                                    "canRead",
                                    read,
                                    "canUpdate",
                                    update)));
            return (String) store.createSession(name).get("token");
        }
    }

    @Test
    public void workflowAdministrationRequiresEffectiveModelAdminReadUpdateAndVisibility()
            throws Exception {
        String body =
                JSON.toJson(
                        Map.of(
                                "modelKey",
                                key,
                                "id",
                                "review",
                                "version",
                                "1",
                                "mode",
                                "initialize",
                                "objectTypes",
                                List.of("PERSON")));
        List<String> denied =
                List.of(
                        user("noRead", false, true, true, false, true),
                        user("noUpdate", false, true, true, true, false),
                        user("noVisibility", false, true, false, true, true),
                        user("noAdmin", true, false, true, true, true),
                        user("noPortal", false, false, true, true, true));
        for (String token : denied) {
            for (String endpoint :
                    List.of(
                            "activation/preview",
                            "activation/commit",
                            "migration/preview",
                            "migration/commit",
                            "deactivate"))
                assertEquals(
                        endpoint,
                        403,
                        request("POST", "/api/workflows/" + endpoint, token, body).statusCode());
            assertEquals(403, request("DELETE", "/api/workflows", token, body).statusCode());
        }
        assertEquals(
                200, request("GET", "/api/workflows?modelKey=" + key, reader, null).statusCode());
        assertEquals(
                403,
                request("GET", "/api/workflows?modelKey=" + key, denied.get(4), null).statusCode());
        String manager = user("manager", false, true, true, true, true);
        assertEquals(
                200,
                request("POST", "/api/workflows/activation/preview", manager, body).statusCode());
        assertEquals(
                200,
                request("POST", "/api/auth/impersonate", admin, "{\"username\":\"noRead\"}")
                        .statusCode());
        assertEquals(
                403,
                request("POST", "/api/workflows/activation/preview", admin, body).statusCode());
        assertEquals(
                403, request("POST", "/api/workflows/migration/preview", admin, body).statusCode());
    }

    @Test
    public void permissionEndpointPersistsAndRevokesDedicatedTransitionFlag() throws Exception {
        String endpoint = "/api/access/users/reader/permissions";
        Map<String, Object> permission =
                new HashMap<>(
                        Map.of(
                                "modelKey",
                                key,
                                "visible",
                                true,
                                "canRead",
                                true,
                                "canTransition",
                                true));
        assertEquals(
                403,
                request(
                                "PUT",
                                endpoint,
                                reader,
                                JSON.toJson(Map.of("permissions", List.of(permission))))
                        .statusCode());
        assertEquals(
                200,
                request(
                                "PUT",
                                endpoint,
                                admin,
                                JSON.toJson(Map.of("permissions", List.of(permission))))
                        .statusCode());
        assertEquals(200, move(reader, "submit", 0).statusCode());
        permission.remove("canTransition");
        assertEquals(
                200,
                request(
                                "PUT",
                                endpoint,
                                admin,
                                JSON.toJson(Map.of("permissions", List.of(permission))))
                        .statusCode());
        assertEquals(403, move(reader, "revise", 1).statusCode());
        var view = request("GET", path() + "?modelKey=" + key, reader, null);
        assertFalse(
                JsonParser.parseString(view.body())
                        .getAsJsonObject()
                        .get("canTransition")
                        .getAsBoolean());
        assertEquals(1, events());
    }
}
