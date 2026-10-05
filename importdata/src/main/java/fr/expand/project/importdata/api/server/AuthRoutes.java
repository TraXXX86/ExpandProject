package fr.expand.project.importdata.api.server;

import static fr.expand.project.importdata.api.server.ApiSupport.*;
import static fr.expand.project.importdata.api.server.ModelMapper.*;

import fr.expand.project.importdata.access.AccessContext;
import fr.expand.project.importdata.access.AccessControlStore;

import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;

import java.util.Map;

final class AuthRoutes {
    private AuthRoutes() {}

    private static final fr.expand.project.importdata.access.LoginThrottle THROTTLE =
            new fr.expand.project.importdata.access.LoginThrottle();

    static void register(RoutesConfig app) {
        app.post("/api/auth/login", ctx -> ctx.result(login(ctx)));
        app.post("/api/auth/logout", ctx -> ctx.result(logout(ctx)));
        app.get("/api/auth/me", ctx -> ctx.result(me(ctx)));
        app.post("/api/auth/impersonate", ctx -> ctx.result(impersonate(ctx)));
        app.post("/api/auth/impersonate/stop", ctx -> ctx.result(stopImpersonation(ctx)));
    }

    static String login(Context ctx) throws Exception {
        ctx.contentType("application/json");
        Map<String, Object> payload = readJsonBody(ctx.body());
        if (payload == null) {
            return error(ctx, 400, "Corps JSON manquant");
        }
        String username = sanitizeUsername(getString(payload.get("username")));
        String password = getString(payload.get("password"));
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return error(ctx, 400, "username/password manquants");
        }
        if (password.length() > 4096)
            return error(ctx, 400, "Mot de passe limité à 4096 caractères");

        String clientKey = ctx.req().getRemoteAddr();
        if (!THROTTLE.tryAcquire(clientKey, username)) {
            ctx.header(
                    "Retry-After", Long.toString(THROTTLE.retryAfterSeconds(clientKey, username)));
            return error(ctx, 429, "Trop de tentatives, réessayez plus tard");
        }
        try (AccessControlStore accessStore = new AccessControlStore()) {
            Map<String, Object> session =
                    accessStore.authenticateAndCreateSession(username, password);
            if (session == null) {
                return error(ctx, 401, "Authentification invalide");
            }
            THROTTLE.clear(clientKey, username);
            String previous = resolveSessionToken(ctx);
            if (previous != null) accessStore.deleteSession(previous);
            AccessContext context = buildAccessContext(accessStore, session);
            if (context == null) {
                return error(ctx, 500, "Impossible de charger le profil utilisateur");
            }
            sessionCookie(
                    ctx,
                    context.getSessionToken(),
                    Math.max(0, context.getExpiresAt() - java.time.Instant.now().getEpochSecond()));
            return GSON.toJson(buildAuthPayload(ctx, context, accessStore));
        } catch (Exception e) {
            throw e;
        }
    }

    static String logout(Context ctx) throws Exception {
        ctx.contentType("application/json");
        sessionCookie(ctx, "", 0);
        String token = resolveSessionToken(ctx);
        if (token == null || token.isBlank()) {
            return GSON.toJson(Map.of("status", "logged-out"));
        }
        try (AccessControlStore accessStore = new AccessControlStore()) {
            accessStore.deleteSession(token);
            return GSON.toJson(Map.of("status", "logged-out"));
        } catch (Exception e) {
            throw e;
        }
    }

    static String me(Context ctx) throws Exception {
        ctx.contentType("application/json");
        AccessContext context = resolveAccessContext(ctx);
        if (context == null) {
            return error(ctx, 401, "Session invalide");
        }
        try (AccessControlStore accessStore = new AccessControlStore()) {
            return GSON.toJson(buildAuthPayload(ctx, context, accessStore));
        } catch (Exception e) {
            throw e;
        }
    }

    static String impersonate(Context ctx) throws Exception {
        ctx.contentType("application/json");
        AccessContext context = resolveAccessContext(ctx);
        if (context == null) {
            return error(ctx, 401, "Session invalide");
        }
        if (!context.isActorPlatformAdmin()) {
            return error(ctx, 403, "Seul un administrateur peut impersonner");
        }
        Map<String, Object> payload = readJsonBody(ctx.body());
        if (payload == null) {
            return error(ctx, 400, "Corps JSON manquant");
        }
        String targetUsername = sanitizeUsername(getString(payload.get("username")));
        if (targetUsername == null || targetUsername.isBlank()) {
            return error(ctx, 400, "username cible manquant");
        }

        try (AccessControlStore accessStore = new AccessControlStore()) {
            boolean ok = accessStore.impersonateSession(context.getSessionToken(), targetUsername);
            if (!ok) {
                return error(ctx, 400, "Impersonation impossible");
            }
            Map<String, Object> session = accessStore.loadSession(context.getSessionToken());
            AccessContext refreshedContext = buildAccessContext(accessStore, session);
            if (refreshedContext == null) {
                return error(ctx, 500, "Impossible de charger la session impersonée");
            }
            sessionCookie(
                    ctx,
                    refreshedContext.getSessionToken(),
                    Math.max(
                            0,
                            refreshedContext.getExpiresAt()
                                    - java.time.Instant.now().getEpochSecond()));
            return GSON.toJson(buildAuthPayload(ctx, refreshedContext, accessStore));
        } catch (Exception e) {
            throw e;
        }
    }

    static String stopImpersonation(Context ctx) throws Exception {
        ctx.contentType("application/json");
        AccessContext context = resolveAccessContext(ctx);
        if (context == null) {
            return error(ctx, 401, "Session invalide");
        }
        if (!context.isActorPlatformAdmin()) {
            return error(ctx, 403, "Seul un administrateur peut arrêter l'impersonation");
        }
        try (AccessControlStore accessStore = new AccessControlStore()) {
            boolean ok = accessStore.stopImpersonation(context.getSessionToken());
            if (!ok) {
                return error(ctx, 400, "Aucune impersonation active");
            }
            Map<String, Object> session = accessStore.loadSession(context.getSessionToken());
            AccessContext refreshedContext = buildAccessContext(accessStore, session);
            if (refreshedContext == null) {
                return error(ctx, 500, "Impossible de charger la session");
            }
            sessionCookie(
                    ctx,
                    refreshedContext.getSessionToken(),
                    Math.max(
                            0,
                            refreshedContext.getExpiresAt()
                                    - java.time.Instant.now().getEpochSecond()));
            return GSON.toJson(buildAuthPayload(ctx, refreshedContext, accessStore));
        } catch (Exception e) {
            throw e;
        }
    }
}
