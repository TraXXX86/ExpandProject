package fr.expand.project.importdata.api.server;

import static org.junit.Assert.*;

import com.google.gson.*;

import fr.expand.project.importdata.access.AccessControlStore;
import fr.expand.project.importdata.dao.Neo4jDriverProvider;
import fr.expand.project.importdata.model.*;

import io.javalin.Javalin;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.net.*;
import java.net.http.*;
import java.util.*;

public class AuditApiIT {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private final Map<String, String> previous = new HashMap<>();
    private String key, admin, editor, hidden, base;
    private Javalin app;

    private void setting(String key, String value) {
        previous.put(key, System.getProperty(key));
        System.setProperty(key, value);
    }

    @Before
    public void setup() throws Exception {
        setting("ACCESS_DB_PATH", temp.newFolder().toPath().resolve("audit.sqlite").toString());
        setting("EXPAND_ADMIN_PASSWORD", UUID.randomUUID().toString());
        String name = "AuditHttpIT_" + UUID.randomUUID().toString().replace("-", "");
        key = name + ":1";
        ModelManager manager = new ModelManager();
        manager.loadModelFromXml(
                "<DATA_MODEL NAME=\""
                        + name
                        + "\" VERSION=\"1\"><OBJECT_TYPES><OBJECT_TYPE"
                        + " NAME=\"PERSON\"><ATTRIBUTE_DEFINITIONS><ATTRIBUTE_DEFINITION"
                        + " NAME=\"NAME\" TYPE=\"STRING\""
                        + " REQUIRED=\"true\"/></ATTRIBUTE_DEFINITIONS></OBJECT_TYPE></OBJECT_TYPES><LINK_TYPES/></DATA_MODEL>");
        try (var models = new Neo4jModelStore()) {
            models.createModel(manager.getCurrentModel(), manager.getCurrentModelXml());
        }
        try (var access = new AccessControlStore()) {
            access.upsertUser("editor", "Editor", true, false, false, UUID.randomUUID().toString());
            access.upsertUser("hidden", "Hidden", true, false, false, UUID.randomUUID().toString());
            access.replaceModelPermissions(
                    "editor",
                    List.of(
                            Map.of(
                                    "modelKey",
                                    key,
                                    "visible",
                                    true,
                                    "canRead",
                                    true,
                                    "canCreate",
                                    true,
                                    "canUpdate",
                                    true,
                                    "canDelete",
                                    true)));
            access.replaceModelPermissions(
                    "hidden",
                    List.of(
                            Map.of(
                                    "modelKey",
                                    key,
                                    "visible",
                                    true,
                                    "canRead",
                                    false,
                                    "canCreate",
                                    true,
                                    "canUpdate",
                                    true,
                                    "canDelete",
                                    true)));
            admin = (String) access.createSession("admin").get("token");
            editor = (String) access.createSession("editor").get("token");
            hidden = (String) access.createSession("hidden").get("token");
        }
        app = ImportApiServer.create().start(0);
        base = "http://127.0.0.1:" + app.port();
    }

    @After
    public void cleanup() {
        try {
            if (app != null) app.stop();
            if (key != null) {
                try (var models = new Neo4jModelStore()) {
                    models.deleteModelAndDataByKey(key);
                }
                try (var session = Neo4jDriverProvider.getDriver().session()) {
                    session.run("MATCH (e:AuditData {modelKey:$key}) DELETE e", Map.of("key", key))
                            .consume();
                }
            }
        } finally {
            previous.forEach(
                    (key, value) -> {
                        if (value == null) System.clearProperty(key);
                        else System.setProperty(key, value);
                    });
        }
    }

    private HttpResponse<String> request(String method, String path, String token, String body)
            throws Exception {
        var builder =
                HttpRequest.newBuilder(URI.create(base + path))
                        .header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        builder.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        return HttpClient.newHttpClient()
                .send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    public void historyUsesEffectiveReadPermissionAndRecordsRealActor() throws Exception {
        String path =
                "/api/history?modelKey="
                        + URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(401, request("GET", path, null, null).statusCode());
        assertEquals(403, request("GET", path, hidden, null).statusCode());
        assertEquals(200, request("GET", path, editor, null).statusCode());
        assertEquals(400, request("GET", path + "&limit=101", editor, null).statusCode());
        assertEquals(400, request("GET", path + "&action=unknown", editor, null).statusCode());
        assertEquals(
                200,
                request("POST", "/api/auth/impersonate", admin, "{\"username\":\"editor\"}")
                        .statusCode());
        assertEquals(
                201,
                request(
                                "POST",
                                "/api/objects",
                                admin,
                                new Gson()
                                        .toJson(
                                                Map.of(
                                                        "modelKey",
                                                        key,
                                                        "type",
                                                        "PERSON",
                                                        "attributes",
                                                        List.of(
                                                                Map.of(
                                                                        "key",
                                                                        "NAME",
                                                                        "value",
                                                                        "From impersonation")))))
                        .statusCode());
        var response = request("GET", path + "&entityType=OBJECT&action=CREATE", editor, null);
        assertEquals(200, response.statusCode());
        var event =
                JsonParser.parseString(response.body())
                        .getAsJsonObject()
                        .getAsJsonArray("items")
                        .get(0)
                        .getAsJsonObject();
        assertEquals("admin", event.get("actor").getAsString());
        assertEquals("editor", event.get("effectiveUser").getAsString());
        assertEquals(
                "From impersonation", event.getAsJsonObject("after").get("NAME").getAsString());
        assertEquals(
                200,
                request("POST", "/api/auth/impersonate", admin, "{\"username\":\"hidden\"}")
                        .statusCode());
        assertEquals(
                403,
                request("GET", path, admin, null)
                        .statusCode()); // actor admin never overrides effective permission
        assertEquals(200, request("POST", "/api/auth/impersonate/stop", admin, "{}").statusCode());
        try (var models = new Neo4jModelStore()) {
            models.deleteModelAndDataByKey(key);
        }
        assertEquals(404, request("GET", path, editor, null).statusCode());
        assertEquals(200, request("GET", path, admin, null).statusCode());
    }
}
