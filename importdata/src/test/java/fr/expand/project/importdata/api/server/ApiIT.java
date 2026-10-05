package fr.expand.project.importdata.api.server;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import fr.expand.project.importdata.model.Neo4jModelStore;

import io.javalin.Javalin;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.MethodSorters;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Real HTTP regression suite; owns its server, temporary auth database and uniquely named graphs.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ApiIT {
    @ClassRule public static TemporaryFolder temp = new TemporaryFolder();
    private static final Gson JSON = new Gson();
    private static final HttpClient HTTP =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
    private static final Map<String, String> previousSettings = new HashMap<>();
    private static final List<String> modelKeys = new ArrayList<>();
    private static Javalin app;
    private static String base;
    private static String admin;
    private static String initialSecret;

    @BeforeClass
    public static void start() throws Exception {
        configure(
                "ACCESS_DB_PATH",
                temp.newFolder("auth").toPath().resolve("access.sqlite").toString());
        initialSecret = UUID.randomUUID().toString();
        configure("EXPAND_ADMIN_PASSWORD", initialSecret);
        configure("EXPAND_ALLOWED_ORIGINS", "http://localhost:5173");
        configure("EXPAND_COOKIE_SECURE", "true");
        app = ImportApiServer.create().start(0);
        base = "http://127.0.0.1:" + app.port();
        admin = token(login("admin", initialSecret));
    }

    @AfterClass
    public static void stop() {
        try {
            if (!modelKeys.isEmpty()) {
                try (Neo4jModelStore store = new Neo4jModelStore()) {
                    for (String key : modelKeys) store.deleteModelAndDataByKey(key);
                }
            }
        } finally {
            if (app != null) app.stop();
            previousSettings.forEach(
                    (key, value) -> {
                        if (value == null) System.clearProperty(key);
                        else System.setProperty(key, value);
                    });
        }
    }

    private static void configure(String key, String value) {
        previousSettings.put(key, System.getProperty(key));
        System.setProperty(key, value);
    }

    private static HttpResponse<String> request(
            String method,
            String path,
            String bearer,
            String type,
            String body,
            Map<String, String> headers)
            throws Exception {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30));
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        if (type != null) builder.header("Content-Type", type);
        headers.forEach(builder::header);
        builder.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> json(String method, String path, String bearer, Object body)
            throws Exception {
        return request(
                method,
                path,
                bearer,
                "application/json",
                body == null ? null : JSON.toJson(body),
                Map.of());
    }

    private static HttpResponse<String> get(String path, String bearer) throws Exception {
        return request("GET", path, bearer, null, null, Map.of());
    }

    private static JsonObject object(HttpResponse<String> response, int expected) {
        // Avoid response dumps: auth response bodies contain opaque credentials.
        assertEquals("HTTP status", expected, response.statusCode());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static HttpResponse<String> login(String username, String password) throws Exception {
        return json(
                "POST",
                "/api/auth/login",
                null,
                Map.of("username", username, "password", password));
    }

    private static String token(HttpResponse<String> response) {
        return object(response, 200).get("token").getAsString();
    }

    private static Map<String, Object> user(String name, String password, boolean modelAdmin) {
        Map<String, Object> body =
                new HashMap<>(
                        Map.of(
                                "username",
                                name,
                                "displayName",
                                name,
                                "portalUser",
                                true,
                                "portalModelAdmin",
                                modelAdmin,
                                "platformAdmin",
                                false));
        if (password != null) body.put("password", password);
        return body;
    }

    private static String query(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String modelPath(String key) {
        return "/api/models/" + query(key);
    }

    private static String dataPath(String key) {
        return "/api/data?modelKey=" + query(key);
    }

    @Test
    public void a_authCookiesCsrfCorsAndPasswordReset() throws Exception {
        assertEquals(401, get("/api/auth/me", null).statusCode());
        assertEquals(401, get("/api/auth/me", "invalid-token").statusCode());
        assertEquals(200, get("/api/health", null).statusCode());
        assertEquals(
                400,
                request("POST", "/api/auth/login", null, "application/json", "{", Map.of())
                        .statusCode());
        assertEquals(
                400,
                request("POST", "/api/auth/login", null, "application/json", "[]", Map.of())
                        .statusCode());
        assertEquals(
                400,
                request("POST", "/api/auth/login", null, "text/plain", "{}", Map.of())
                        .statusCode());
        assertEquals(400, login("u".repeat(129), "incorrect").statusCode());
        assertEquals(400, login("admin", "p".repeat(4097)).statusCode());
        HttpResponse<String> allowed =
                request(
                        "OPTIONS",
                        "/api/models",
                        null,
                        null,
                        null,
                        Map.of(
                                "Origin",
                                "http://localhost:5173",
                                "Access-Control-Request-Method",
                                "POST"));
        assertEquals(204, allowed.statusCode());
        assertEquals(
                "http://localhost:5173",
                allowed.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertEquals(
                "true",
                allowed.headers().firstValue("Access-Control-Allow-Credentials").orElseThrow());
        assertEquals(
                403,
                request(
                                "GET",
                                "/api/health",
                                null,
                                null,
                                null,
                                Map.of("Origin", "https://untrusted.invalid"))
                        .statusCode());

        HttpResponse<String> browserLogin =
                request(
                        "POST",
                        "/api/auth/login",
                        null,
                        "application/json",
                        JSON.toJson(Map.of("username", "admin", "password", initialSecret)),
                        Map.of("X-Requested-With", "ExpandProject"));
        assertFalse(object(browserLogin, 200).has("token"));
        String setCookie = browserLogin.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Strict"));
        assertTrue(setCookie.contains("Secure"));
        assertTrue(setCookie.contains("Path=/api"));
        String cookie = setCookie.substring(0, setCookie.indexOf(';'));
        assertEquals(
                400,
                request(
                                "GET",
                                "/api/auth/me",
                                null,
                                null,
                                null,
                                Map.of("Cookie", cookie, "X-HTTP-Method-Override", "POST"))
                        .statusCode());
        assertEquals(
                200,
                request("GET", "/api/auth/me", null, null, null, Map.of("Cookie", cookie))
                        .statusCode());
        assertEquals(
                403,
                request(
                                "POST",
                                "/api/auth/logout",
                                null,
                                "application/json",
                                "{}",
                                Map.of("Cookie", cookie))
                        .statusCode());
        int malformedAuthorization =
                request(
                                "POST",
                                "/api/access/users",
                                null,
                                "application/json",
                                "{}",
                                Map.of("Cookie", cookie, "Authorization", "Basic invalid"))
                        .statusCode();
        assertTrue(
                "Malformed Authorization must not bypass cookie CSRF",
                malformedAuthorization == 401 || malformedAuthorization == 403);
        HttpResponse<String> logout =
                request(
                        "POST",
                        "/api/auth/logout",
                        null,
                        "application/json",
                        "{}",
                        Map.of("Cookie", cookie, "X-Requested-With", "ExpandProject"));
        assertEquals(200, logout.statusCode());
        assertTrue(logout.headers().firstValue("Set-Cookie").orElseThrow().contains("Max-Age=0"));
        assertEquals(
                401,
                request("GET", "/api/auth/me", null, null, null, Map.of("Cookie", cookie))
                        .statusCode());

        String name = "reset_" + UUID.randomUUID().toString().replace("-", "");
        String password = UUID.randomUUID().toString();
        assertEquals(
                400,
                json("POST", "/api/access/users", admin, user(name, null, false)).statusCode());
        object(json("POST", "/api/access/users", admin, user(name, password, false)), 201);
        assertEquals(
                409,
                json("POST", "/api/access/users", admin, user(name, password, false)).statusCode());
        String worker = token(login(name, password));
        object(json("PUT", "/api/access/users/" + name, admin, user(name, null, false)), 200);
        assertEquals(200, get("/api/auth/me", worker).statusCode());
        assertEquals(
                400,
                json("PUT", "/api/access/users/" + name, admin, user(name, "p".repeat(4097), false))
                        .statusCode());
        assertEquals(200, get("/api/auth/me", worker).statusCode());
        String impersonator = token(login("admin", initialSecret));
        object(json("POST", "/api/auth/impersonate", impersonator, Map.of("username", name)), 200);
        assertEquals(
                name,
                object(get("/api/auth/me", impersonator), 200)
                        .getAsJsonObject("user")
                        .get("username")
                        .getAsString());
        String replacement = UUID.randomUUID().toString();
        object(
                json("PUT", "/api/access/users/" + name, admin, user(name, replacement, false)),
                200);
        assertEquals(401, get("/api/auth/me", worker).statusCode());
        assertEquals(401, get("/api/auth/me", impersonator).statusCode());
        assertEquals(401, login(name, password).statusCode());
        assertEquals(200, get("/api/auth/me", token(login(name, replacement))).statusCode());
    }

    private static String xml(String name) {
        return """
<DATA_MODEL NAME="%s" VERSION="1"><OBJECT_TYPES><OBJECT_TYPE NAME="PERSON"><ATTRIBUTE_DEFINITIONS>
<ATTRIBUTE_DEFINITION NAME="NAME" TYPE="STRING" REQUIRED="true" SEARCHABLE="true"/>
</ATTRIBUTE_DEFINITIONS></OBJECT_TYPE></OBJECT_TYPES><LINK_TYPES><LINK_TYPE NAME="KNOWS" DIRECTED="false">
<SOURCE_TYPES><TYPE_REF NAME="PERSON"/></SOURCE_TYPES><TARGET_TYPES><TYPE_REF NAME="PERSON"/></TARGET_TYPES>
<ATTRIBUTE_DEFINITIONS><ATTRIBUTE_DEFINITION NAME="NOTE" TYPE="STRING"/></ATTRIBUTE_DEFINITIONS>
</LINK_TYPE></LINK_TYPES></DATA_MODEL>
"""
                .formatted(name);
    }

    private static HttpResponse<String> multipart(
            String path, String bearer, String fileField, String file, Map<String, String> fields)
            throws Exception {
        String boundary = "ApiIT" + UUID.randomUUID().toString().replace("-", "");
        StringBuilder body = new StringBuilder();
        fields.forEach(
                (name, value) ->
                        body.append("--")
                                .append(boundary)
                                .append("\r\nContent-Disposition: form-data; name=\"")
                                .append(name)
                                .append("\"\r\n\r\n")
                                .append(value)
                                .append("\r\n"));
        body.append("--")
                .append(boundary)
                .append("\r\nContent-Disposition: form-data; name=\"")
                .append(fileField)
                .append("\"; filename=\"fixture.xml\"\r\nContent-Type: application/xml\r\n\r\n")
                .append(file)
                .append("\r\n--")
                .append(boundary)
                .append("--\r\n");
        return request(
                "POST",
                path,
                bearer,
                "multipart/form-data; boundary=" + boundary,
                body.toString(),
                Map.of());
    }

    private static String createModel() throws Exception {
        String name = "ApiIT_" + UUID.randomUUID().toString().replace("-", "");
        String key = name + ":1";
        modelKeys.add(key);
        assertEquals(
                key,
                object(multipart("/api/models", admin, "modelFile", xml(name), Map.of()), 201)
                        .get("key")
                        .getAsString());
        return key;
    }

    private static List<Map<String, String>> attrs(String key, String value) {
        return List.of(Map.of("key", key, "value", value));
    }

    private static Map<String, Object> person(String key, int externalId, String name) {
        return Map.of(
                "modelKey",
                key,
                "type",
                "PERSON",
                "id",
                externalId,
                "attributes",
                attrs("NAME", name));
    }

    private static long createPerson(String key, int externalId, String name, String bearer)
            throws Exception {
        return object(json("POST", "/api/objects", bearer, person(key, externalId, name)), 201)
                .get("id")
                .getAsLong();
    }

    private static String attribute(JsonObject row, String key) {
        for (var attr : row.getAsJsonArray("attributes")) {
            JsonObject value = attr.getAsJsonObject();
            if (key.equals(value.get("key").getAsString())) return value.get("value").getAsString();
        }
        return null;
    }

    @Test
    public void b_modelDataImportsLiteralValuesPaginationNeighborsAndRollback() throws Exception {
        String key = createModel();
        String other = createModel();
        String modelXml = xml(key.substring(0, key.length() - 2));
        assertEquals(
                400,
                multipart("/api/models", admin, "modelFile", "<DATA_MODEL>", Map.of())
                        .statusCode());
        String entityXml =
                "<!DOCTYPE DATA_MODEL [<!ENTITY field 'NAME'>]>"
                        + modelXml.replace("NAME=\"NAME\"", "NAME=\"&field;\"");
        assertEquals(
                400,
                multipart("/api/models", admin, "modelFile", entityXml, Map.of()).statusCode());
        assertEquals(
                409, multipart("/api/models", admin, "modelFile", modelXml, Map.of()).statusCode());
        assertEquals(
                400,
                request(
                                "PUT",
                                modelPath(key),
                                admin,
                                "application/xml",
                                xml(other.substring(0, other.length() - 2)),
                                Map.of())
                        .statusCode());
        assertEquals(key, object(get(modelPath(key), admin), 200).get("key").getAsString());
        assertEquals(other, object(get(modelPath(other), admin), 200).get("key").getAsString());

        String invalidBatch =
                "<DATAS><OBJECTS><OBJECT ID=\"90\" TYPE=\"PERSON\"><ATTRIBUTE KEY=\"NAME\""
                        + " VALUE=\"Valid first\"/></OBJECT><OBJECT ID=\"91\""
                        + " TYPE=\"PERSON\"><ATTRIBUTE KEY=\"NAME\" VALUE=\"Invalid"
                        + " second\"/><ATTRIBUTE KEY=\"modelKey\""
                        + " VALUE=\"escape\"/></OBJECT></OBJECTS><LINKS/></DATAS>";
        assertEquals(
                400,
                multipart("/api/data", admin, "dataFile", invalidBatch, Map.of("modelKey", key))
                        .statusCode());
        assertEquals(0, object(get(dataPath(key), admin), 200).get("totalObjects").getAsInt());
        String validBatch =
                "<DATAS><OBJECTS><OBJECT ID=\"3\" TYPE=\"PERSON\"><ATTRIBUTE KEY=\"NAME\""
                    + " VALUE=\"Imported &quot;name&quot;\"/></OBJECT></OBJECTS><LINKS/></DATAS>";
        assertTrue(
                object(
                                multipart(
                                        "/api/data",
                                        admin,
                                        "dataFile",
                                        validBatch,
                                        Map.of("modelKey", key, "validateOnly", "true")),
                                200)
                        .get("valid")
                        .getAsBoolean());
        assertEquals(0, object(get(dataPath(key), admin), 200).get("totalObjects").getAsInt());
        String literal = "O'Brien \\ \"quoted\" ') DETACH DELETE n //";
        long first = createPerson(key, 0, literal, admin);
        long second = createPerson(key, 1, "Alpha child", admin);
        long third = createPerson(key, 2, "Zulu", admin);
        object(multipart("/api/data", admin, "dataFile", validBatch, Map.of("modelKey", key)), 200);
        String duplicateBatch =
                "<DATAS><OBJECTS><OBJECT ID=\"8\" TYPE=\"PERSON\"><ATTRIBUTE KEY=\"NAME\""
                        + " VALUE=\"Rollback new\"/></OBJECT><OBJECT ID=\"0\""
                        + " TYPE=\"PERSON\"><ATTRIBUTE KEY=\"NAME\""
                        + " VALUE=\"Duplicate\"/></OBJECT></OBJECTS><LINKS/></DATAS>";
        assertEquals(
                409,
                multipart("/api/data", admin, "dataFile", duplicateBatch, Map.of("modelKey", key))
                        .statusCode());
        JsonObject all = object(get(dataPath(key), admin), 200);
        assertEquals(4, all.get("totalObjects").getAsInt());
        JsonObject literalObject = null;
        for (var entry : all.getAsJsonArray("objects")) {
            if (entry.getAsJsonObject().get("id").getAsLong() == first) {
                literalObject = entry.getAsJsonObject();
            }
        }
        assertNotNull(literalObject);
        assertEquals(literal, attribute(literalObject, "NAME"));
        assertEquals(0, literalObject.get("dataId").getAsInt());
        assertEquals(
                400,
                json(
                                "POST",
                                "/api/objects",
                                admin,
                                Map.of("modelKey", key, "type", "PERSON", "attributes", List.of()))
                        .statusCode());
        assertEquals(
                400,
                request("POST", "/api/objects", admin, "application/json", "{", Map.of())
                        .statusCode());
        assertEquals(
                404,
                json(
                                "PUT",
                                "/api/objects/" + first,
                                admin,
                                Map.of("modelKey", other, "attributes", attrs("NAME", "foreign")))
                        .statusCode());
        assertEquals(
                400,
                json(
                                "PUT",
                                "/api/objects/" + first,
                                admin,
                                Map.of("modelKey", key, "attributes", attrs("modelKey", other)))
                        .statusCode());
        assertEquals(
                400,
                json(
                                "PUT",
                                "/api/objects/" + first,
                                admin,
                                Map.of(
                                        "modelKey",
                                        key,
                                        "attributes",
                                        List.of(
                                                Map.of("key", "NAME", "value", "one"),
                                                Map.of("key", "NAME", "value", "two"))))
                        .statusCode());

        long link =
                object(
                                json(
                                        "POST",
                                        "/api/links",
                                        admin,
                                        Map.of(
                                                "modelKey",
                                                key,
                                                "type",
                                                "KNOWS",
                                                "fromId",
                                                first,
                                                "toId",
                                                second,
                                                "attributes",
                                                attrs("NOTE", literal))),
                                201)
                        .get("id")
                        .getAsLong();
        object(
                json(
                        "POST",
                        "/api/links",
                        admin,
                        Map.of(
                                "modelKey",
                                key,
                                "type",
                                "KNOWS",
                                "fromId",
                                first,
                                "toId",
                                third,
                                "attributes",
                                attrs("NOTE", "second"))),
                201);
        JsonObject page = object(get(dataPath(key) + "&limit=1", admin), 200);
        assertEquals(1, page.getAsJsonArray("objects").size());
        assertEquals(0, page.getAsJsonArray("links").size());
        assertTrue(page.get("hasMore").getAsBoolean());
        assertEquals(4, page.get("totalObjects").getAsInt());
        JsonObject next = object(get(dataPath(key) + "&offset=1&limit=1", admin), 200);
        assertNotEquals(
                page.getAsJsonArray("objects").get(0).getAsJsonObject().get("id").getAsLong(),
                next.getAsJsonArray("objects").get(0).getAsJsonObject().get("id").getAsLong());
        assertEquals(400, get(dataPath(key) + "&limit=501", admin).statusCode());
        assertEquals(400, get(dataPath(key) + "&offset=-1", admin).statusCode());
        assertEquals(
                1,
                object(get(dataPath(key) + "&q=ALPHA&type=PERSON", admin), 200)
                        .get("totalObjects")
                        .getAsInt());
        JsonObject neighbors =
                object(
                        get(
                                "/api/objects/"
                                        + first
                                        + "/neighbors?modelKey="
                                        + query(key)
                                        + "&limit=1",
                                admin),
                        200);
        assertEquals(2, neighbors.getAsJsonArray("objects").size());
        assertEquals(1, neighbors.getAsJsonArray("links").size());
        assertTrue(neighbors.get("hasMore").getAsBoolean());
        assertEquals(
                404,
                get("/api/objects/" + first + "/neighbors?modelKey=" + query(other), admin)
                        .statusCode());
        // Numeric IDs remain compatible, while browser mutations use the stable UUID from a
        // listing.
        object(
                json(
                        "PUT",
                        "/api/links/" + link,
                        admin,
                        Map.of(
                                "modelKey",
                                key,
                                "attributes",
                                attrs("NOTE", "numeric compatibility"))),
                200);
        JsonObject listed = null;
        for (var entry : object(get(dataPath(key), admin), 200).getAsJsonArray("links")) {
            if (entry.getAsJsonObject().get("id").getAsLong() == link)
                listed = entry.getAsJsonObject();
        }
        assertNotNull(listed);
        String linkUuid = listed.get("uuid").getAsString();
        assertNotNull(UUID.fromString(linkUuid));
        assertEquals(
                404,
                json(
                                "PUT",
                                "/api/links/" + linkUuid,
                                admin,
                                Map.of("modelKey", other, "attributes", attrs("NOTE", "foreign")))
                        .statusCode());
        object(
                json(
                        "PUT",
                        "/api/links/" + linkUuid,
                        admin,
                        Map.of("modelKey", key, "attributes", attrs("NOTE", "updated ' literal"))),
                200);
        JsonArray links = object(get(dataPath(key), admin), 200).getAsJsonArray("links");
        JsonObject updated = null;
        for (var entry : links)
            if (entry.getAsJsonObject().get("id").getAsLong() == link)
                updated = entry.getAsJsonObject();
        assertNotNull(updated);
        assertEquals("updated ' literal", attribute(updated, "NOTE"));
        assertFalse(updated.get("directed").getAsBoolean());
        assertEquals(
                404,
                request(
                                "DELETE",
                                "/api/links/" + linkUuid + "?modelKey=" + query(other),
                                admin,
                                null,
                                null,
                                Map.of())
                        .statusCode());
        object(
                request(
                        "DELETE",
                        "/api/links/" + linkUuid + "?modelKey=" + query(key),
                        admin,
                        null,
                        null,
                        Map.of()),
                200);
        assertEquals(1, object(get(dataPath(key), admin), 200).getAsJsonArray("links").size());
    }

    private static void permissions(
            String username,
            String key,
            boolean read,
            boolean create,
            boolean update,
            boolean delete)
            throws Exception {
        object(
                json(
                        "PUT",
                        "/api/access/users/" + username + "/permissions",
                        admin,
                        Map.of(
                                "permissions",
                                List.of(
                                        Map.of(
                                                "modelKey",
                                                key,
                                                "visible",
                                                true,
                                                "canRead",
                                                read,
                                                "canCreate",
                                                create,
                                                "canUpdate",
                                                update,
                                                "canDelete",
                                                delete)))),
                200);
    }

    @Test
    public void c_nonAdminCapabilitiesSeparateVisibilityFromReadAndMutation() throws Exception {
        String key = createModel();
        String name = "editor_" + UUID.randomUUID().toString().replace("-", "");
        String password = UUID.randomUUID().toString();
        object(json("POST", "/api/access/users", admin, user(name, password, true)), 201);
        String editor = token(login(name, password));
        permissions(name, key, true, false, false, false);
        String unassigned = createModel();
        assertEquals(403, get(modelPath(unassigned), editor).statusCode());
        assertEquals(403, get(dataPath(unassigned), editor).statusCode());
        assertEquals(200, get(modelPath(key), editor).statusCode());
        assertEquals(200, get(dataPath(key), editor).statusCode());
        assertEquals(
                403, json("POST", "/api/objects", editor, person(key, 0, "Blocked")).statusCode());
        assertEquals(
                403,
                request(
                                "PUT",
                                modelPath(key),
                                editor,
                                "application/xml",
                                xml(key.substring(0, key.length() - 2)),
                                Map.of())
                        .statusCode());
        assertEquals(
                403, request("DELETE", modelPath(key), editor, null, null, Map.of()).statusCode());
        assertEquals(
                403,
                json("POST", "/api/access/users", editor, user("unwanted", password, false))
                        .statusCode());
        permissions(name, key, false, true, false, false);
        long id = createPerson(key, 0, "Created without read", editor);
        assertEquals(403, get(dataPath(key), editor).statusCode());
        assertEquals(
                403,
                json(
                                "PUT",
                                "/api/objects/" + id,
                                editor,
                                Map.of("modelKey", key, "attributes", attrs("NAME", "Blocked")))
                        .statusCode());
        assertEquals(
                403,
                request(
                                "DELETE",
                                "/api/objects/" + id + "?modelKey=" + query(key),
                                editor,
                                null,
                                null,
                                Map.of())
                        .statusCode());
        permissions(name, key, true, false, true, false);
        object(
                json(
                        "PUT",
                        "/api/objects/" + id,
                        editor,
                        Map.of("modelKey", key, "attributes", attrs("NAME", "Allowed update"))),
                200);
        assertEquals(
                "Allowed update",
                attribute(
                        object(get(dataPath(key), editor), 200)
                                .getAsJsonArray("objects")
                                .get(0)
                                .getAsJsonObject(),
                        "NAME"));
        object(
                request(
                        "PUT",
                        modelPath(key),
                        editor,
                        "application/xml",
                        xml(key.substring(0, key.length() - 2)),
                        Map.of()),
                200);
        permissions(name, key, true, false, false, true);
        object(
                request(
                        "DELETE",
                        "/api/objects/" + id + "?modelKey=" + query(key),
                        editor,
                        null,
                        null,
                        Map.of()),
                200);
        assertEquals(0, object(get(dataPath(key), editor), 200).get("totalObjects").getAsInt());
        object(request("DELETE", modelPath(key), editor, null, null, Map.of()), 200);
        assertEquals(404, get(modelPath(key), admin).statusCode());
    }

    @Test
    public void z_loginThrottleReturnsRetryAfterForRepeatedUnknownAccount() throws Exception {
        String unknown = "missing_" + UUID.randomUUID().toString().replace("-", "");
        for (int i = 0; i < 10; i++) assertEquals(401, login(unknown, "incorrect").statusCode());
        HttpResponse<String> blocked = login(unknown, "incorrect");
        assertEquals(429, blocked.statusCode());
        long retry = Long.parseLong(blocked.headers().firstValue("Retry-After").orElseThrow());
        assertTrue(retry > 0 && retry <= 300);
    }
}
