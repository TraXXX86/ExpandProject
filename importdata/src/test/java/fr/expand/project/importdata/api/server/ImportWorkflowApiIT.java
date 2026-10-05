package fr.expand.project.importdata.api.server;

import static org.junit.Assert.*;

import com.google.gson.JsonParser;

import fr.expand.project.importdata.access.AccessControlStore;
import fr.expand.project.importdata.model.*;

import io.javalin.Javalin;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

public class ImportWorkflowApiIT {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private Javalin app;
    private String base, key, admin, reader, creator;
    private Neo4jModelStore models;
    private final Map<String, String> previous = new HashMap<>();
    private static final HttpClient HTTP =
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @Before
    public void setup() throws Exception {
        configure("ACCESS_DB_PATH", temp.newFile("access.sqlite").toString());
        configure("EXPAND_ADMIN_PASSWORD", UUID.randomUUID().toString());
        String name = "ImportApiIT_" + UUID.randomUUID().toString().replace("-", "");
        key = name + ":1";
        var manager = new ModelManager();
        manager.loadModelFromXml(
                """
<DATA_MODEL NAME="%s" VERSION="1"><OBJECT_TYPES><OBJECT_TYPE NAME="PERSON"><ATTRIBUTE_DEFINITIONS><ATTRIBUTE_DEFINITION NAME="NAME" TYPE="STRING" REQUIRED="true" SEARCHABLE="true"/></ATTRIBUTE_DEFINITIONS></OBJECT_TYPE></OBJECT_TYPES><LINK_TYPES/></DATA_MODEL>
"""
                        .formatted(name));
        models = new Neo4jModelStore();
        models.createModel(manager.getCurrentModel(), manager.getCurrentModelXml());
        try (var access = new AccessControlStore()) {
            admin = access.createSession("admin").get("token").toString();
            access.upsertUser("reader", "reader", true, false, false, "import-test-password");
            access.upsertUser("creator", "creator", true, false, false, "import-test-password");
            access.replaceModelPermissions(
                    "reader",
                    List.of(
                            Map.of(
                                    "modelKey",
                                    key,
                                    "visible",
                                    true,
                                    "canRead",
                                    true,
                                    "canCreate",
                                    false,
                                    "canUpdate",
                                    false,
                                    "canDelete",
                                    false)));
            access.replaceModelPermissions(
                    "creator",
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
                                    false,
                                    "canDelete",
                                    false)));
            reader = access.createSession("reader").get("token").toString();
            creator = access.createSession("creator").get("token").toString();
        }
        app = ImportApiServer.create().start(0);
        base = "http://127.0.0.1:" + app.port();
    }

    private void configure(String key, String value) {
        previous.put(key, System.getProperty(key));
        System.setProperty(key, value);
    }

    @After
    public void cleanup() {
        try {
            if (models != null) models.deleteModelAndDataByKey(key);
        } finally {
            if (app != null) app.stop();
            previous.forEach(
                    (k, v) -> {
                        if (v == null) System.clearProperty(k);
                        else System.setProperty(k, v);
                    });
        }
    }

    private HttpResponse<String> upload(
            String endpoint, String token, String csv, Map<String, String> extra) throws Exception {
        String boundary = "ImportBoundary" + UUID.randomUUID().toString().replace("-", "");
        Map<String, String> fields =
                new LinkedHashMap<>(
                        Map.of(
                                "modelKey",
                                key,
                                "format",
                                "csv",
                                "objectType",
                                "PERSON",
                                "idColumn",
                                "id",
                                "mapping",
                                "{\"NAME\":\"name\"}",
                                "mode",
                                "upsert"));
        fields.putAll(extra);
        StringBuilder body = new StringBuilder();
        fields.forEach(
                (k, v) ->
                        body.append("--")
                                .append(boundary)
                                .append("\r\nContent-Disposition: form-data; name=\"")
                                .append(k)
                                .append("\"\r\n\r\n")
                                .append(v)
                                .append("\r\n"));
        body.append("--")
                .append(boundary)
                .append(
                        "\r\n"
                                + "Content-Disposition: form-data; name=\"file\";"
                                + " filename=\"data.csv\"\r\n"
                                + "Content-Type: text/csv\r\n\r\n")
                .append(csv)
                .append("\r\n--")
                .append(boundary)
                .append("--\r\n");
        var request =
                HttpRequest.newBuilder(URI.create(base + "/api/imports/" + endpoint))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String hash(String token, String csv) throws Exception {
        var response = upload("preview", token, csv, Map.of());
        assertEquals(response.body(), 200, response.statusCode());
        return JsonParser.parseString(response.body())
                .getAsJsonObject()
                .get("previewHash")
                .getAsString();
    }

    @Test
    public void guardsAndCommitRequireCurrentPreview() throws Exception {
        String initial = "id,name\n0,Alice";
        for (String endpoint : List.of("inspect", "preview", "commit")) {
            assertEquals(401, upload(endpoint, null, initial, Map.of()).statusCode());
            assertEquals(403, upload(endpoint, reader, initial, Map.of()).statusCode());
        }
        var inspected = upload("inspect", creator, initial, Map.of());
        assertEquals(200, inspected.statusCode());
        assertEquals(
                "id",
                JsonParser.parseString(inspected.body())
                        .getAsJsonObject()
                        .getAsJsonArray("columns")
                        .get(0)
                        .getAsString());
        assertEquals(
                400, upload("preview", creator, initial, Map.of("mapping", "[]")).statusCode());
        assertEquals(400, upload("commit", creator, initial, Map.of()).statusCode());
        String first = hash(creator, initial);
        var created = upload("commit", creator, initial, Map.of("previewHash", first));
        assertEquals(created.body(), 200, created.statusCode());
        assertEquals(
                409, upload("commit", creator, initial, Map.of("previewHash", first)).statusCode());
        String updated = "id,name\n0,Updated\n1,New", next = hash(creator, updated);
        assertEquals(
                403, upload("commit", creator, updated, Map.of("previewHash", next)).statusCode());
        assertEquals(
                200, upload("commit", admin, updated, Map.of("previewHash", next)).statusCode());
        assertEquals(
                409, upload("commit", admin, updated, Map.of("previewHash", next)).statusCode());
    }

    @Test
    public void malformedInputsAreClientErrorsAndRowsCarryValidationErrors() throws Exception {
        for (Map<String, String> options :
                List.of(
                        Map.of("objectType", ""),
                        Map.of("idColumn", "missing"),
                        Map.of("mapping", "{\"NAME\":null}"),
                        Map.of("mode", "replace"),
                        Map.of("format", "exe")))
            assertEquals(400, upload("preview", admin, "id,name\n0,Alice", options).statusCode());
        var response = upload("preview", admin, "id,name\n0,Alice\nwrong,Bob\n1,", Map.of());
        assertEquals(response.body(), 200, response.statusCode());
        var preview = JsonParser.parseString(response.body()).getAsJsonObject();
        assertFalse(preview.get("valid").getAsBoolean());
        assertEquals(2, preview.getAsJsonObject("summary").get("conflicts").getAsInt());
    }
}
