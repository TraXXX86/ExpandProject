package fr.expand.project.importdata.api.server;

import java.util.List;
import java.util.Map;

import static spark.Spark.delete;
import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.put;

import fr.expand.project.importdata.access.AccessContext;
import fr.expand.project.importdata.access.AccessControlStore;

final class AccessRoutes {

    private final ApiSupport apiSupport;
    private final AccessSupport accessSupport;

    AccessRoutes(ApiSupport apiSupport, AccessSupport accessSupport) {
        this.apiSupport = apiSupport;
        this.accessSupport = accessSupport;
    }

    void register() {
        get("/api/access/me", (request, response) -> {
            response.type("application/json");
            AccessContext context = accessSupport.resolveAccessContext(request, response);
            if (context == null) {
                return apiSupport.error(response, 401, "Session invalide");
            }

            try (AccessControlStore accessStore = new AccessControlStore()) {
                Map<String, Object> payload = accessStore.loadUserAccess(context.getUsername());
                if (payload == null) {
                    return apiSupport.error(response, 404, "Utilisateur introuvable");
                }
                payload.put("auth", accessSupport.buildAuthMeta(context));
                return apiSupport.toJson(payload);
            }
        });

        get("/api/access/users", (request, response) -> {
            response.type("application/json");
            AccessContext context = accessSupport.resolveAccessContext(request, response);
            if (context == null) {
                return apiSupport.error(response, 401, "Utilisateur inconnu");
            }

            try (AccessControlStore accessStore = new AccessControlStore()) {
                if (context.isActorPlatformAdmin()) {
                    return apiSupport.toJson(accessStore.listUsers());
                }
                Map<String, Object> self = accessStore.loadUser(context.getUsername());
                if (self == null) {
                    return apiSupport.toJson(List.of());
                }
                return apiSupport.toJson(List.of(self));
            }
        });

        get("/api/access/users/:username/access", (request, response) -> {
            response.type("application/json");
            String username = apiSupport.sanitizeUsername(request.params("username"));
            if (username == null || username.isBlank()) {
                return apiSupport.error(response, 400, "username manquant");
            }

            AccessContext context = accessSupport.resolveAccessContext(request, response);
            if (context == null) {
                return apiSupport.error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isActorPlatformAdmin() && !context.getUsername().equals(username)) {
                return apiSupport.error(response, 403, "Accès refusé");
            }

            try (AccessControlStore accessStore = new AccessControlStore()) {
                Map<String, Object> payload = accessStore.loadUserAccess(username);
                if (payload == null) {
                    return apiSupport.error(response, 404, "Utilisateur introuvable");
                }
                return apiSupport.toJson(payload);
            }
        });

        post("/api/access/users", (request, response) -> {
            response.type("application/json");
            AccessContext context = accessSupport.resolveAccessContext(request, response);
            if (context == null) {
                return apiSupport.error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isActorPlatformAdmin()) {
                return apiSupport.error(response, 403, "Droits insuffisants");
            }

            Map<String, Object> payload = apiSupport.readJsonBody(request.body());
            if (payload == null) {
                return apiSupport.error(response, 400, "Corps JSON manquant");
            }

            String username = apiSupport.sanitizeUsername(apiSupport.getString(payload.get("username")));
            String displayName = apiSupport.getString(payload.get("displayName"));
            if (username == null || username.isBlank()) {
                return apiSupport.error(response, 400, "username manquant");
            }

            boolean portalUser = apiSupport.getBoolean(payload.get("portalUser"));
            boolean portalModelAdmin = apiSupport.getBoolean(payload.get("portalModelAdmin"));
            boolean platformAdmin = apiSupport.getBoolean(payload.get("platformAdmin"));
            String password = apiSupport.getString(payload.get("password"));

            try (AccessControlStore accessStore = new AccessControlStore()) {
                accessStore.upsertUser(username, displayName, portalUser, portalModelAdmin, platformAdmin, password);
                Map<String, Object> created = accessStore.loadUserAccess(username);
                return apiSupport.toJson(created);
            }
        });

        put("/api/access/users/:username", (request, response) -> {
            response.type("application/json");
            AccessContext context = accessSupport.resolveAccessContext(request, response);
            if (context == null) {
                return apiSupport.error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isActorPlatformAdmin()) {
                return apiSupport.error(response, 403, "Droits insuffisants");
            }

            String username = apiSupport.sanitizeUsername(request.params("username"));
            if (username == null || username.isBlank()) {
                return apiSupport.error(response, 400, "username manquant");
            }

            Map<String, Object> payload = apiSupport.readJsonBody(request.body());
            if (payload == null) {
                return apiSupport.error(response, 400, "Corps JSON manquant");
            }

            String displayName = apiSupport.getString(payload.get("displayName"));
            boolean portalUser = apiSupport.getBoolean(payload.get("portalUser"));
            boolean portalModelAdmin = apiSupport.getBoolean(payload.get("portalModelAdmin"));
            boolean platformAdmin = apiSupport.getBoolean(payload.get("platformAdmin"));
            String password = apiSupport.getString(payload.get("password"));

            try (AccessControlStore accessStore = new AccessControlStore()) {
                if (accessStore.loadUser(username) == null) {
                    return apiSupport.error(response, 404, "Utilisateur introuvable");
                }
                accessStore.upsertUser(username, displayName, portalUser, portalModelAdmin, platformAdmin, password);
                Map<String, Object> updated = accessStore.loadUserAccess(username);
                return apiSupport.toJson(updated);
            }
        });

        delete("/api/access/users/:username", (request, response) -> {
            response.type("application/json");
            AccessContext context = accessSupport.resolveAccessContext(request, response);
            if (context == null) {
                return apiSupport.error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isActorPlatformAdmin()) {
                return apiSupport.error(response, 403, "Droits insuffisants");
            }

            String username = apiSupport.sanitizeUsername(request.params("username"));
            if (username == null || username.isBlank()) {
                return apiSupport.error(response, 400, "username manquant");
            }

            try (AccessControlStore accessStore = new AccessControlStore()) {
                boolean deleted = accessStore.deleteUser(username);
                if (!deleted) {
                    return apiSupport.error(response, 400, "Suppression refusée ou utilisateur introuvable");
                }
                return apiSupport.toJson(Map.of("status", "deleted", "username", username));
            }
        });

        put("/api/access/users/:username/permissions", (request, response) -> {
            response.type("application/json");
            AccessContext context = accessSupport.resolveAccessContext(request, response);
            if (context == null) {
                return apiSupport.error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isActorPlatformAdmin()) {
                return apiSupport.error(response, 403, "Droits insuffisants");
            }

            String username = apiSupport.sanitizeUsername(request.params("username"));
            if (username == null || username.isBlank()) {
                return apiSupport.error(response, 400, "username manquant");
            }

            Map<String, Object> payload = apiSupport.readJsonBody(request.body());
            if (payload == null) {
                return apiSupport.error(response, 400, "Corps JSON manquant");
            }

            List<Map<String, Object>> permissions = apiSupport.readPermissionList(payload.get("permissions"));
            try (AccessControlStore accessStore = new AccessControlStore()) {
                if (accessStore.loadUser(username) == null) {
                    return apiSupport.error(response, 404, "Utilisateur introuvable");
                }
                accessStore.replaceModelPermissions(username, permissions);
                Map<String, Object> updated = accessStore.loadUserAccess(username);
                return apiSupport.toJson(updated);
            }
        });
    }
}
