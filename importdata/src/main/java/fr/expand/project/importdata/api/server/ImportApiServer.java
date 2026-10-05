package fr.expand.project.importdata.api.server;

import static fr.expand.project.importdata.api.server.ApiSupport.*;

import fr.expand.project.importdata.access.AccessControlStore;

import io.javalin.Javalin;
import io.javalin.config.SizeUnit;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpResponseException;

import jakarta.xml.bind.JAXBException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** HTTP lifecycle and transport policy. Domain routes are registered separately. */
public final class ImportApiServer {
    private static final Logger LOGGER = LogManager.getLogger(ImportApiServer.class);
    private static Javalin running;

    private ImportApiServer() {}

    public static void main(String[] args) {
        start(args.length == 0 ? 8080 : Integer.parseInt(args[0]));
    }

    public static synchronized void start(int port) {
        if (running != null) throw new IllegalStateException("API already running");
        running = create().start(port);
    }

    public static synchronized void stop() {
        if (running != null) {
            running.stop();
            running = null;
        }
    }

    /** Separate factory lets integration tests own a server bound to an ephemeral port. */
    public static Javalin create() {
        try (AccessControlStore ignored = new AccessControlStore()) {
            /* fail fast on invalid bootstrap */
        }
        Set<String> origins =
                Arrays.stream(
                                setting(
                                                "EXPAND_ALLOWED_ORIGINS",
                                                "http://localhost:5173,http://127.0.0.1:5173")
                                        .split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
        if (origins.contains("*"))
            throw new IllegalArgumentException("CORS requires explicit origins");
        return Javalin.create(
                config -> {
                    config.http.maxRequestSize = 10 * 1024 * 1024;
                    config.jetty.multipartConfig.maxFileSize(10, SizeUnit.MB);
                    config.jetty.multipartConfig.maxTotalRequestSize(10, SizeUnit.MB);
                    config.jetty.multipartConfig.maxInMemoryFileSize(64, SizeUnit.KB);
                    config.routes.before(
                            ctx -> {
                                ctx.contentType("application/json; charset=utf-8");
                                ctx.header("X-Content-Type-Options", "nosniff");
                                ctx.header("Cache-Control", "no-store");
                                String origin = ctx.header("Origin");
                                if (origin != null) {
                                    String ownOrigin =
                                            ctx.req().getScheme()
                                                    + "://"
                                                    + ctx.req().getHeader("Host");
                                    if (!origins.contains(origin) && !origin.equals(ownOrigin))
                                        throw new ForbiddenResponse("Origine non autorisée");
                                    ctx.header("Access-Control-Allow-Origin", origin);
                                    ctx.header("Access-Control-Allow-Credentials", "true");
                                    ctx.header("Vary", "Origin");
                                    ctx.header(
                                            "Access-Control-Allow-Methods",
                                            "GET,POST,PUT,DELETE,OPTIONS");
                                    ctx.header(
                                            "Access-Control-Allow-Headers",
                                            "Content-Type,Authorization,X-Requested-With");
                                }
                                if (ctx.header("X-HTTP-Method-Override") != null)
                                    throw new BadRequestResponse(
                                            "Method override non pris en charge");
                                String method = ctx.req().getMethod();
                                boolean mutation =
                                        Set.of("POST", "PUT", "PATCH", "DELETE").contains(method);
                                if (mutation
                                        && ctx.cookie(SESSION_COOKIE) != null
                                        && ctx.header("Authorization") == null
                                        && !"ExpandProject".equals(ctx.header("X-Requested-With")))
                                    throw new ForbiddenResponse(
                                            "En-tête de protection CSRF manquant");
                                if ("/api/auth/login".equals(ctx.path())
                                        && mutation
                                        && !ctx.isJson())
                                    throw new BadRequestResponse(
                                            "Content-Type application/json requis");
                            });
                    config.routes.options("/*", ctx -> ctx.status(204).result(""));
                    config.routes.exception(
                            HttpResponseException.class,
                            (e, ctx) -> ctx.result(error(ctx, e.getStatus(), e.getMessage())));
                    config.routes.exception(
                            fr.expand.project.importdata.dao.StorageConflictException.class,
                            (e, ctx) -> ctx.result(error(ctx, 409, e.getMessage())));
                    config.routes.exception(
                            org.neo4j.driver.exceptions.ClientException.class,
                            (e, ctx) -> {
                                if (e.code().contains("Constraint"))
                                    ctx.result(
                                            error(ctx, 409, "Conflit avec une donnée existante"));
                                else {
                                    LOGGER.error("Database request failed", e);
                                    ctx.result(error(ctx, 500, "Erreur de base de données"));
                                }
                            });
                    config.routes.exception(
                            IllegalArgumentException.class,
                            (e, ctx) -> ctx.result(error(ctx, 400, e.getMessage())));
                    config.routes.exception(
                            JAXBException.class,
                            (e, ctx) ->
                                    ctx.result(
                                            error(
                                                    ctx,
                                                    400,
                                                    "XML invalide ou non conforme au schéma")));
                    config.routes.exception(
                            Exception.class,
                            (e, ctx) -> {
                                String reference = java.util.UUID.randomUUID().toString();
                                LOGGER.error(
                                        "Request failed [{}] {} {}",
                                        reference,
                                        ctx.req().getMethod(),
                                        ctx.path(),
                                        e);
                                ctx.result(
                                        error(
                                                ctx,
                                                500,
                                                "Erreur interne (référence " + reference + ")"));
                            });
                    HealthRoutes.register(config.routes);
                    AuthRoutes.register(config.routes);
                    AccessRoutes.register(config.routes);
                    ModelRoutes.register(config.routes);
                    DataRoutes.register(config.routes);
                    SavedViewRoutes.register(config.routes);
                    ImportWorkflowRoutes.register(config.routes);
                    HistoryRoutes.register(config.routes);
                    GraphInsightsRoutes.register(config.routes);
                    WorkflowAdminRoutes.register(config.routes);
                    WorkflowObjectRoutes.register(config.routes);
                });
    }

    static String setting(String key, String fallback) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
