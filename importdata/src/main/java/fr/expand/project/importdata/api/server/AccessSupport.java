package fr.expand.project.importdata.api.server;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fr.expand.project.importdata.access.AccessContext;
import fr.expand.project.importdata.access.AccessControlStore;
import spark.Request;
import spark.Response;

final class AccessSupport {

    private final ApiSupport apiSupport;

    AccessSupport(ApiSupport apiSupport) {
        this.apiSupport = apiSupport;
    }

    AccessContext resolveAccessContext(Request request, Response response) {
        String token = resolveSessionToken(request);
        if (token == null || token.isBlank()) {
            return null;
        }
        try (AccessControlStore accessStore = new AccessControlStore()) {
            Map<String, Object> session = accessStore.loadSession(token);
            return buildAccessContext(accessStore, session);
        } catch (Exception e) {
            response.status(500);
            return null;
        }
    }

    AccessContext buildAccessContext(AccessControlStore accessStore, Map<String, Object> session) {
        if (accessStore == null || session == null) {
            return null;
        }

        String token = apiSupport.getString(session.get("token"));
        String actorUsername = apiSupport.sanitizeUsername(apiSupport.getString(session.get("actorUsername")));
        String effectiveUsername = apiSupport.sanitizeUsername(apiSupport.getString(session.get("effectiveUsername")));
        if (token == null || token.isBlank() || actorUsername == null || effectiveUsername == null) {
            return null;
        }

        Map<String, Object> actorUser = accessStore.loadUser(actorUsername);
        Map<String, Object> effectiveUser = accessStore.loadUser(effectiveUsername);
        if (actorUser == null || effectiveUser == null) {
            return null;
        }

        List<Map<String, Object>> permissions = accessStore.listModelPermissions(effectiveUsername);
        boolean impersonating = apiSupport.getBoolean(session.get("impersonating"));
        Long expiresAt = apiSupport.getLong(session.get("expiresAt"));

        return new AccessContext(
            token,
            actorUser,
            effectiveUser,
            permissions,
            impersonating,
            expiresAt == null ? 0L : expiresAt
        );
    }

    String resolveSessionToken(Request request) {
        String authHeader = request.headers("Authorization");
        String token = AccessContext.extractBearerToken(authHeader);
        if (token != null && !token.isBlank()) {
            return token;
        }

        String fallback = request.headers("X-Session-Token");
        if (fallback == null || fallback.isBlank()) {
            return null;
        }
        return fallback.trim();
    }

    Map<String, Object> buildAuthPayload(AccessContext context, AccessControlStore accessStore) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", context.getSessionToken());
        payload.put("auth", buildAuthMeta(context));
        payload.put("user", accessStore.loadUser(context.getUsername()));
        payload.put("permissions", accessStore.listModelPermissions(context.getUsername()));
        return payload;
    }

    Map<String, Object> buildAuthMeta(AccessContext context) {
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
}
