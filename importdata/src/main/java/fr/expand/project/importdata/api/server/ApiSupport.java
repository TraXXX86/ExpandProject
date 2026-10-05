package fr.expand.project.importdata.api.server;

import static fr.expand.project.importdata.api.server.ApiSupport.*;
import static fr.expand.project.importdata.api.server.ModelMapper.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import fr.expand.project.importdata.access.AccessContext;
import fr.expand.project.importdata.access.AccessControlStore;
import fr.expand.project.importdata.dto.DataPackAttribute;
import fr.expand.project.importdata.dto.generated.DATAS;
import fr.expand.project.importdata.model.ModelManager;
import fr.expand.project.importdata.model.Neo4jModelStore;
import fr.expand.project.importdata.model.generated.LINKTYPE;
import fr.expand.project.importdata.model.generated.TYPEREF;

import io.javalin.http.Context;

import jakarta.xml.bind.JAXBException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ApiSupport {
    static final String SESSION_COOKIE = "expand_session";
    static final Gson GSON = new GsonBuilder().serializeNulls().create();

    static int countObjects(DATAS data) {
        if (data.getOBJECTS() == null || data.getOBJECTS().getOBJECT() == null) {
            return 0;
        }
        return data.getOBJECTS().getOBJECT().size();
    }

    static int countLinks(DATAS data) {
        if (data.getLINKS() == null || data.getLINKS().getLINK() == null) {
            return 0;
        }
        return data.getLINKS().getLINK().size();
    }

    static String error(Context ctx, int status, String message) {
        ctx.status(status);
        return GSON.toJson(Map.of("error", message));
    }

    static Map<String, Object> readJsonBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = GSON.fromJson(body, Map.class);
            return payload;
        } catch (Exception e) {
            return null;
        }
    }

    static String getString(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    static Integer getInt(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new java.math.BigDecimal(value.toString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    static Long getLong(Object value) {
        if (value == null) return null;
        try {
            return new java.math.BigDecimal(value.toString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    static boolean getBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        return Boolean.parseBoolean(value.toString());
    }

    static List<DataPackAttribute> readAttributes(Object value) {
        List<DataPackAttribute> attributes = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return attributes;
        }
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            Object keyValue = map.get("key");
            if (keyValue == null) {
                continue;
            }
            String key = keyValue.toString();
            String val = map.get("value") == null ? "" : map.get("value").toString();
            attributes.add(new DataPackAttribute(key, val));
        }
        return attributes;
    }

    static List<DataPackAttribute> readAttributesFromMap(Map<String, Object> attributes) {
        List<DataPackAttribute> rows = new ArrayList<>();
        if (attributes == null) {
            return rows;
        }
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            rows.add(
                    new DataPackAttribute(
                            key, entry.getValue() == null ? "" : entry.getValue().toString()));
        }
        return rows;
    }

    static Map<String, Object> attributesToMap(List<DataPackAttribute> attributes) {
        Map<String, Object> map = new HashMap<>();
        if (attributes == null) {
            return map;
        }
        for (DataPackAttribute attribute : attributes) {
            if (attribute == null || attribute.getKEY() == null || attribute.getKEY().isBlank()) {
                continue;
            }
            map.put(attribute.getKEY(), attribute.getVALUE() == null ? "" : attribute.getVALUE());
        }
        return map;
    }

    static List<Map<String, Object>> readPermissionList(Object value) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return rows;
        }
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            String modelKey = map.get("modelKey") == null ? null : map.get("modelKey").toString();
            if (modelKey == null || modelKey.isBlank()) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("modelKey", modelKey);
            row.put("visible", getBoolean(map.get("visible")));
            row.put("canRead", getBoolean(map.get("canRead")));
            row.put("canCreate", getBoolean(map.get("canCreate")));
            row.put("canUpdate", getBoolean(map.get("canUpdate")));
            row.put("canDelete", getBoolean(map.get("canDelete")));
            row.put("canTransition", getBoolean(map.get("canTransition")));
            rows.add(row);
        }
        return rows;
    }

    static String sanitizeUsername(String username) {
        if (username == null) {
            return null;
        }
        String value = username.trim();
        if (value.isBlank() || value.length() > 128) {
            return null;
        }
        if (!value.matches("[A-Za-z0-9._-]+")) {
            return null;
        }
        return value;
    }

    static AccessContext resolveAccessContext(Context ctx) {
        String token = resolveSessionToken(ctx);
        if (token == null || token.isBlank()) {
            return null;
        }
        try (AccessControlStore accessStore = new AccessControlStore()) {
            Map<String, Object> session = accessStore.loadSession(token);
            return buildAccessContext(accessStore, session);
        }
    }

    static AccessContext buildAccessContext(
            AccessControlStore accessStore, Map<String, Object> session) {
        if (accessStore == null || session == null) {
            return null;
        }
        String token = getString(session.get("token"));
        String actorUsername = sanitizeUsername(getString(session.get("actorUsername")));
        String effectiveUsername = sanitizeUsername(getString(session.get("effectiveUsername")));
        if (token == null
                || token.isBlank()
                || actorUsername == null
                || effectiveUsername == null) {
            return null;
        }

        Map<String, Object> actorUser = accessStore.loadUser(actorUsername);
        Map<String, Object> effectiveUser = accessStore.loadUser(effectiveUsername);
        if (actorUser == null || effectiveUser == null) {
            return null;
        }

        List<Map<String, Object>> permissions = accessStore.listModelPermissions(effectiveUsername);
        boolean impersonating = getBoolean(session.get("impersonating"));
        long expiresAt =
                getLong(session.get("expiresAt")) == null ? 0L : getLong(session.get("expiresAt"));

        return new AccessContext(
                token, actorUser, effectiveUser, permissions, impersonating, expiresAt);
    }

    static String resolveSessionToken(Context ctx) {
        String authHeader = ctx.header("Authorization");
        String token = AccessContext.extractBearerToken(authHeader);
        if (authHeader != null) return token;
        String fallback = ctx.cookie(SESSION_COOKIE);
        if (fallback == null || fallback.isBlank()) {
            return null;
        }
        return fallback.trim();
    }

    static Map<String, Object> buildAuthPayload(
            Context ctx, AccessContext context, AccessControlStore accessStore) {
        Map<String, Object> payload = new HashMap<>();
        // Browser sessions stay HttpOnly; CLI clients may request a bearer token.
        if (!"ExpandProject".equals(ctx.header("X-Requested-With"))
                && ctx.cookie(SESSION_COOKIE) == null)
            payload.put("token", context.getSessionToken());
        payload.put("auth", buildAuthMeta(context));
        payload.put("user", accessStore.loadUser(context.getUsername()));
        payload.put("permissions", accessStore.listModelPermissions(context.getUsername()));
        return payload;
    }

    static Map<String, Object> buildAuthMeta(AccessContext context) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("actorUsername", context.getActorUsername());
        meta.put("actorDisplayName", context.getActorDisplayName());
        meta.put("effectiveUsername", context.getUsername());
        meta.put("effectiveDisplayName", context.getDisplayName());
        meta.put("impersonating", context.isImpersonating());
        meta.put("actorPlatformAdmin", context.isActorPlatformAdmin());
        meta.put("expiresAt", context.getExpiresAt());
        return meta;
    }

    static boolean isLinkTypeAllowed(
            ModelManager modelManager, LINKTYPE linkType, String candidateType, boolean source) {
        if (candidateType == null || candidateType.isBlank() || linkType == null) {
            return false;
        }
        List<TYPEREF> refs = null;
        if (source) {
            if (linkType.getSOURCETYPES() != null) {
                refs = linkType.getSOURCETYPES().getTYPEREF();
            }
        } else {
            if (linkType.getTARGETTYPES() != null) {
                refs = linkType.getTARGETTYPES().getTYPEREF();
            }
        }
        if (refs == null || refs.isEmpty()) {
            return false;
        }
        for (TYPEREF ref : refs) {
            if (ref == null || ref.getNAME() == null) {
                continue;
            }
            if (modelManager.isTypeOrSubtype(candidateType, ref.getNAME())) {
                return true;
            }
        }
        return false;
    }

    static String readMultipartText(Context ctx, String name) throws java.io.IOException {
        var file = ctx.uploadedFile(name);
        if (file == null) return null;
        try (InputStream input = file.content()) {
            byte[] data = input.readNBytes(10 * 1024 * 1024 + 1);
            if (data.length > 10 * 1024 * 1024)
                throw new io.javalin.http.HttpResponseException(413, "Fichier trop volumineux");
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    static String readMultipartField(Context ctx, String name) {
        return ctx.formParam(name);
    }

    static void sessionCookie(Context ctx, String token, long maxAge) {
        boolean secure =
                Boolean.parseBoolean(
                        ImportApiServer.setting(
                                "EXPAND_COOKIE_SECURE", Boolean.toString(ctx.req().isSecure())));
        ctx.header(
                "Set-Cookie",
                SESSION_COOKIE
                        + "="
                        + token
                        + "; Path=/api; HttpOnly; SameSite=Strict; Max-Age="
                        + maxAge
                        + (secure ? "; Secure" : ""));
    }

    static int pageArgument(Context ctx, String name, int fallback, int min, int max) {
        String value = ctx.queryParam(name);
        if (value == null) return fallback;
        Integer number = getInt(value);
        if (number == null || number < min || number > max)
            throw new IllegalArgumentException(
                    name + " doit être compris entre " + min + " et " + max);
        return number;
    }

    static ModelManager loadModelContext(String key) throws JAXBException {
        try (Neo4jModelStore store = new Neo4jModelStore()) {
            String xml = store.loadModelXmlByKey(key);
            if (xml == null || xml.isBlank())
                throw new io.javalin.http.NotFoundResponse("Modèle introuvable");
            ModelManager manager = new ModelManager();
            manager.loadModelFromXml(xml);
            return manager;
        }
    }
}
