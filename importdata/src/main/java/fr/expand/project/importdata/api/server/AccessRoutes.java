package fr.expand.project.importdata.api.server;

import static fr.expand.project.importdata.api.server.ApiSupport.*;
import static fr.expand.project.importdata.api.server.ModelMapper.*;

import fr.expand.project.importdata.access.AccessContext;
import fr.expand.project.importdata.access.AccessControlStore;

import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;

final class AccessRoutes {
    private AccessRoutes() {}

    static void register(RoutesConfig app) {
        app.get("/api/access/me", ctx -> ctx.result(myAccess(ctx)));
        app.get("/api/access/users", ctx -> ctx.result(listUsers(ctx)));
        app.get("/api/access/users/{username}/access", ctx -> ctx.result(userAccess(ctx)));
        app.post("/api/access/users", ctx -> ctx.result(createUser(ctx)));
        app.put("/api/access/users/{username}", ctx -> ctx.result(updateUser(ctx)));
        app.delete("/api/access/users/{username}", ctx -> ctx.result(deleteUser(ctx)));
        app.put(
                "/api/access/users/{username}/permissions",
                ctx -> ctx.result(updatePermissions(ctx)));
    }

    static String myAccess(Context ctx) throws Exception {
        ctx.contentType("application/json");
        AccessContext context = resolveAccessContext(ctx);
        if (context == null) {
            return error(ctx, 401, "Session invalide");
        }
        try (AccessControlStore accessStore = new AccessControlStore()) {
            Map<String, Object> payload = accessStore.loadUserAccess(context.getUsername());
            if (payload == null) {
                return error(ctx, 404, "Utilisateur introuvable");
            }
            payload.put("auth", buildAuthMeta(context));
            return GSON.toJson(payload);
        }
    }

    static String listUsers(Context ctx) throws Exception {
        ctx.contentType("application/json");
        AccessContext context = resolveAccessContext(ctx);
        if (context == null) {
            return error(ctx, 401, "Utilisateur inconnu");
        }
        try (AccessControlStore accessStore = new AccessControlStore()) {
            if (context.isActorPlatformAdmin()) {
                return GSON.toJson(accessStore.listUsers());
            }
            Map<String, Object> self = accessStore.loadUser(context.getUsername());
            if (self == null) {
                return GSON.toJson(List.of());
            }
            return GSON.toJson(List.of(self));
        }
    }

    static String userAccess(Context ctx) throws Exception {
        ctx.contentType("application/json");
        String username = sanitizeUsername(ctx.pathParam("username"));
        if (username == null || username.isBlank()) {
            return error(ctx, 400, "username manquant");
        }
        AccessContext context = resolveAccessContext(ctx);
        if (context == null) {
            return error(ctx, 401, "Utilisateur inconnu");
        }
        if (!context.isActorPlatformAdmin() && !context.getUsername().equals(username)) {
            return error(ctx, 403, "Accès refusé");
        }
        try (AccessControlStore accessStore = new AccessControlStore()) {
            Map<String, Object> payload = accessStore.loadUserAccess(username);
            if (payload == null) {
                return error(ctx, 404, "Utilisateur introuvable");
            }
            return GSON.toJson(payload);
        }
    }

    static String createUser(Context ctx) throws Exception {
        ctx.contentType("application/json");
        AccessContext context = resolveAccessContext(ctx);
        if (context == null) {
            return error(ctx, 401, "Utilisateur inconnu");
        }
        if (!context.isActorPlatformAdmin()) {
            return error(ctx, 403, "Droits insuffisants");
        }

        Map<String, Object> payload = readJsonBody(ctx.body());
        if (payload == null) {
            return error(ctx, 400, "Corps JSON manquant");
        }

        String username = sanitizeUsername(getString(payload.get("username")));
        String displayName = getString(payload.get("displayName"));
        if (username == null || username.isBlank()) {
            return error(ctx, 400, "username manquant");
        }

        boolean portalUser = getBoolean(payload.get("portalUser"));
        boolean portalModelAdmin = getBoolean(payload.get("portalModelAdmin"));
        boolean platformAdmin = getBoolean(payload.get("platformAdmin"));
        String password = getString(payload.get("password"));

        try (AccessControlStore accessStore = new AccessControlStore()) {
            if (accessStore.loadUser(username) != null)
                return error(ctx, 409, "Utilisateur déjà existant");
            accessStore.upsertUser(
                    username, displayName, portalUser, portalModelAdmin, platformAdmin, password);
            ctx.status(201);
            Map<String, Object> created = accessStore.loadUserAccess(username);
            return GSON.toJson(created);
        }
    }

    static String updateUser(Context ctx) throws Exception {
        ctx.contentType("application/json");
        AccessContext context = resolveAccessContext(ctx);
        if (context == null) {
            return error(ctx, 401, "Utilisateur inconnu");
        }
        if (!context.isActorPlatformAdmin()) {
            return error(ctx, 403, "Droits insuffisants");
        }

        String username = sanitizeUsername(ctx.pathParam("username"));
        if (username == null || username.isBlank()) {
            return error(ctx, 400, "username manquant");
        }

        Map<String, Object> payload = readJsonBody(ctx.body());
        if (payload == null) {
            return error(ctx, 400, "Corps JSON manquant");
        }

        String displayName = getString(payload.get("displayName"));
        boolean portalUser = getBoolean(payload.get("portalUser"));
        boolean portalModelAdmin = getBoolean(payload.get("portalModelAdmin"));
        boolean platformAdmin = getBoolean(payload.get("platformAdmin"));
        String password = getString(payload.get("password"));
        if (password != null && password.isBlank()) password = null;

        try (AccessControlStore accessStore = new AccessControlStore()) {
            if (accessStore.loadUser(username) == null) {
                return error(ctx, 404, "Utilisateur introuvable");
            }
            accessStore.upsertUser(
                    username, displayName, portalUser, portalModelAdmin, platformAdmin, password);
            Map<String, Object> updated = accessStore.loadUserAccess(username);
            return GSON.toJson(updated);
        }
    }

    static String deleteUser(Context ctx) throws Exception {
        ctx.contentType("application/json");
        AccessContext context = resolveAccessContext(ctx);
        if (context == null) {
            return error(ctx, 401, "Utilisateur inconnu");
        }
        if (!context.isActorPlatformAdmin()) {
            return error(ctx, 403, "Droits insuffisants");
        }
        String username = sanitizeUsername(ctx.pathParam("username"));
        if (username == null || username.isBlank()) {
            return error(ctx, 400, "username manquant");
        }

        try (AccessControlStore accessStore = new AccessControlStore()) {
            boolean deleted = accessStore.deleteUser(username);
            if (!deleted) {
                return error(ctx, 400, "Suppression refusée ou utilisateur introuvable");
            }
            return GSON.toJson(Map.of("status", "deleted", "username", username));
        }
    }

    static String updatePermissions(Context ctx) throws Exception {
        ctx.contentType("application/json");
        AccessContext context = resolveAccessContext(ctx);
        if (context == null) {
            return error(ctx, 401, "Utilisateur inconnu");
        }
        if (!context.isActorPlatformAdmin()) {
            return error(ctx, 403, "Droits insuffisants");
        }

        String username = sanitizeUsername(ctx.pathParam("username"));
        if (username == null || username.isBlank()) {
            return error(ctx, 400, "username manquant");
        }

        Map<String, Object> payload = readJsonBody(ctx.body());
        if (payload == null) {
            return error(ctx, 400, "Corps JSON manquant");
        }

        List<Map<String, Object>> permissions = readPermissionList(payload.get("permissions"));
        try (AccessControlStore accessStore = new AccessControlStore()) {
            if (accessStore.loadUser(username) == null) {
                return error(ctx, 404, "Utilisateur introuvable");
            }
            accessStore.replaceModelPermissions(username, permissions);
            Map<String, Object> updated = accessStore.loadUserAccess(username);
            return GSON.toJson(updated);
        }
    }
}
