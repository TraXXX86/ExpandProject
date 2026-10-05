package fr.expand.project.importdata.api.server;

import static org.junit.Assert.*;

import com.google.gson.*;

import fr.expand.project.importdata.access.AccessControlStore;
import fr.expand.project.importdata.model.*;

import io.javalin.Javalin;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class SavedViewIT {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private final Gson gson = new Gson();
    private final HttpClient http = HttpClient.newHttpClient();
    private String base;

    private HttpResponse<String> request(String method, String path, String token, Object body)
            throws Exception {
        var r =
                HttpRequest.newBuilder(URI.create(base + path))
                        .header("Content-Type", "application/json");
        if (token != null) r.header("Authorization", "Bearer " + token);
        return http.send(
                r.method(
                                method,
                                body == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String login(String user) throws Exception {
        var response =
                request(
                        "POST",
                        "/api/auth/login",
                        null,
                        Map.of("username", user, "password", "test-" + user + "-password"));
        assertEquals(response.body(), 200, response.statusCode());
        return JsonParser.parseString(response.body()).getAsJsonObject().get("token").getAsString();
    }

    @Test
    public void sharingNeverGrantsModelAccessAndOnlyOwnerCanMutate() throws Exception {
        String oldPath = System.getProperty("ACCESS_DB_PATH"),
                oldPassword = System.getProperty("EXPAND_ADMIN_PASSWORD");
        String key = null;
        Javalin app = null;
        try {
            System.setProperty(
                    "ACCESS_DB_PATH", temp.newFile("views-http.sqlite").getAbsolutePath());
            System.setProperty("EXPAND_ADMIN_PASSWORD", "test-admin-password");
            String xml =
                    Files.readString(
                                    Path.of(
                                            "src/main/resources/model/example_social_network_model.xml"))
                            .replace(
                                    "SocialNetworkModel",
                                    "Views_" + UUID.randomUUID().toString().replace("-", ""));
            ModelManager manager = new ModelManager();
            manager.loadModelFromXml(xml);
            try (Neo4jModelStore models = new Neo4jModelStore()) {
                key = models.createModel(manager.getCurrentModel(), xml);
            }
            try (AccessControlStore users = new AccessControlStore()) {
                for (String user : List.of("alice", "bob", "denied"))
                    users.upsertUser(user, user, true, false, false, "test-" + user + "-password");
                for (String user : List.of("alice", "bob"))
                    users.replaceModelPermissions(
                            user,
                            List.of(Map.of("modelKey", key, "visible", true, "canRead", true)));
            }
            app = ImportApiServer.create().start(0);
            base = "http://127.0.0.1:" + app.port();
            String alice = login("alice"), bob = login("bob"), denied = login("denied");
            String query = "?modelKey=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
            String noPortal;
            try (AccessControlStore users = new AccessControlStore()) {
                users.upsertUser(
                        "noportal", "No portal", false, true, false, "test-noportal-password");
                users.replaceModelPermissions(
                        "noportal",
                        List.of(Map.of("modelKey", key, "visible", true, "canRead", true)));
                noPortal = (String) users.createSession("noportal").get("token");
            }
            for (String endpoint :
                    List.of("/api/views", "/api/history", "/api/quality", "/api/graph/paths"))
                assertEquals(403, request("GET", endpoint + query, noPortal, null).statusCode());

            for (String endpoint : List.of("/api/graph/paths", "/api/quality")) {
                assertEquals(401, request("GET", endpoint + query, null, null).statusCode());
                assertEquals(403, request("GET", endpoint + query, denied, null).statusCode());
            }
            assertEquals(200, request("GET", "/api/quality" + query, bob, null).statusCode());
            assertEquals(400, request("GET", "/api/graph/paths" + query, bob, null).statusCode());
            var body =
                    Map.of(
                            "modelKey",
                            key,
                            "name",
                            "Lecture partagée",
                            "shared",
                            true,
                            "state",
                            Map.of("page", "table", "tableSearch", "O'Brien"));
            var created = request("POST", "/api/views", alice, body);
            assertEquals(created.body(), 201, created.statusCode());
            String id =
                    JsonParser.parseString(created.body())
                            .getAsJsonObject()
                            .get("id")
                            .getAsString();
            assertEquals(401, request("GET", "/api/views" + query, null, null).statusCode());
            assertEquals(403, request("GET", "/api/views" + query, denied, null).statusCode());
            var listed = request("GET", "/api/views" + query, bob, null);
            assertEquals(200, listed.statusCode());
            assertEquals(
                    1,
                    JsonParser.parseString(listed.body())
                            .getAsJsonObject()
                            .getAsJsonArray("items")
                            .size());
            assertEquals(403, request("PUT", "/api/views/" + id, bob, body).statusCode());
            assertEquals(403, request("DELETE", "/api/views/" + id, bob, null).statusCode());
            try (AccessControlStore users = new AccessControlStore()) {
                users.replaceModelPermissions("bob", List.of());
            }
            assertEquals(403, request("GET", "/api/views" + query, bob, null).statusCode());
            assertEquals(403, request("PUT", "/api/views/" + id, bob, body).statusCode());
            assertEquals(200, request("DELETE", "/api/views/" + id, alice, null).statusCode());
            assertEquals(404, request("DELETE", "/api/views/" + id, alice, null).statusCode());
        } finally {
            if (app != null) app.stop();
            if (key != null)
                try (Neo4jModelStore models = new Neo4jModelStore()) {
                    models.deleteModelAndDataByKey(key);
                }
            if (oldPath == null) System.clearProperty("ACCESS_DB_PATH");
            else System.setProperty("ACCESS_DB_PATH", oldPath);
            if (oldPassword == null) System.clearProperty("EXPAND_ADMIN_PASSWORD");
            else System.setProperty("EXPAND_ADMIN_PASSWORD", oldPassword);
        }
    }
}
