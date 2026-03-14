package fr.expand.project.importdata.api.server;

import java.util.Map;

import static spark.Spark.get;
import static spark.Spark.post;

import fr.expand.project.importdata.access.AccessContext;
import fr.expand.project.importdata.access.AccessControlStore;

final class AuthRoutes {

    private final ApiSupport apiSupport;
    private final AccessSupport accessSupport;

    AuthRoutes(ApiSupport apiSupport, AccessSupport accessSupport) {
        this.apiSupport = apiSupport;
        this.accessSupport = accessSupport;
    }

    void register() {
        post("/api/auth/login", (request, response) -> {
            response.type("application/json");
            Map<String, Object> payload = apiSupport.readJsonBody(request.body());
            if (payload == null) {
                return apiSupport.error(response, 400, "Corps JSON manquant");
            }

            String username = apiSupport.sanitizeUsername(apiSupport.getString(payload.get("username")));
            String password = apiSupport.getString(payload.get("password"));
            if (username == null || username.isBlank() || password == null || password.isBlank()) {
                return apiSupport.error(response, 400, "username/password manquants");
            }

            try (AccessControlStore accessStore = new AccessControlStore()) {
                Map<String, Object> user = accessStore.authenticate(username, password);
                if (user == null) {
                    return apiSupport.error(response, 401, "Authentification invalide");
                }

                Map<String, Object> session = accessStore.createSession(username);
                if (session == null) {
                    return apiSupport.error(response, 500, "Impossible de créer la session");
                }

                AccessContext context = accessSupport.buildAccessContext(accessStore, session);
                if (context == null) {
                    return apiSupport.error(response, 500, "Impossible de charger le profil utilisateur");
                }
                return apiSupport.toJson(accessSupport.buildAuthPayload(context, accessStore));
            } catch (Exception e) {
                return apiSupport.error(response, 500, "Erreur d'authentification: " + e.getMessage());
            }
        });

        post("/api/auth/logout", (request, response) -> {
            response.type("application/json");
            String token = accessSupport.resolveSessionToken(request);
            if (token == null || token.isBlank()) {
                return apiSupport.toJson(Map.of("status", "logged-out"));
            }

            try (AccessControlStore accessStore = new AccessControlStore()) {
                accessStore.deleteSession(token);
                return apiSupport.toJson(Map.of("status", "logged-out"));
            } catch (Exception e) {
                return apiSupport.error(response, 500, "Erreur lors de la déconnexion: " + e.getMessage());
            }
        });

        get("/api/auth/me", (request, response) -> {
            response.type("application/json");
            AccessContext context = accessSupport.resolveAccessContext(request, response);
            if (context == null) {
                return apiSupport.error(response, 401, "Session invalide");
            }

            try (AccessControlStore accessStore = new AccessControlStore()) {
                return apiSupport.toJson(accessSupport.buildAuthPayload(context, accessStore));
            } catch (Exception e) {
                return apiSupport.error(response, 500, "Erreur lors du chargement de la session: " + e.getMessage());
            }
        });

        post("/api/auth/impersonate", (request, response) -> {
            response.type("application/json");
            AccessContext context = accessSupport.resolveAccessContext(request, response);
            if (context == null) {
                return apiSupport.error(response, 401, "Session invalide");
            }
            if (!context.isActorPlatformAdmin()) {
                return apiSupport.error(response, 403, "Seul un administrateur peut impersonner");
            }

            Map<String, Object> payload = apiSupport.readJsonBody(request.body());
            if (payload == null) {
                return apiSupport.error(response, 400, "Corps JSON manquant");
            }

            String targetUsername = apiSupport.sanitizeUsername(apiSupport.getString(payload.get("username")));
            if (targetUsername == null || targetUsername.isBlank()) {
                return apiSupport.error(response, 400, "username cible manquant");
            }

            try (AccessControlStore accessStore = new AccessControlStore()) {
                boolean ok = accessStore.impersonateSession(context.getSessionToken(), targetUsername);
                if (!ok) {
                    return apiSupport.error(response, 400, "Impersonation impossible");
                }

                Map<String, Object> session = accessStore.loadSession(context.getSessionToken());
                AccessContext refreshedContext = accessSupport.buildAccessContext(accessStore, session);
                if (refreshedContext == null) {
                    return apiSupport.error(response, 500, "Impossible de charger la session impersonée");
                }
                return apiSupport.toJson(accessSupport.buildAuthPayload(refreshedContext, accessStore));
            } catch (Exception e) {
                return apiSupport.error(response, 500, "Erreur lors de l'impersonation: " + e.getMessage());
            }
        });

        post("/api/auth/impersonate/stop", (request, response) -> {
            response.type("application/json");
            AccessContext context = accessSupport.resolveAccessContext(request, response);
            if (context == null) {
                return apiSupport.error(response, 401, "Session invalide");
            }
            if (!context.isActorPlatformAdmin()) {
                return apiSupport.error(response, 403, "Seul un administrateur peut arrêter l'impersonation");
            }

            try (AccessControlStore accessStore = new AccessControlStore()) {
                boolean ok = accessStore.stopImpersonation(context.getSessionToken());
                if (!ok) {
                    return apiSupport.error(response, 400, "Aucune impersonation active");
                }

                Map<String, Object> session = accessStore.loadSession(context.getSessionToken());
                AccessContext refreshedContext = accessSupport.buildAccessContext(accessStore, session);
                if (refreshedContext == null) {
                    return apiSupport.error(response, 500, "Impossible de charger la session");
                }
                return apiSupport.toJson(accessSupport.buildAuthPayload(refreshedContext, accessStore));
            } catch (Exception e) {
                return apiSupport.error(response, 500, "Erreur lors de l'arrêt de l'impersonation: " + e.getMessage());
            }
        });
    }
}
