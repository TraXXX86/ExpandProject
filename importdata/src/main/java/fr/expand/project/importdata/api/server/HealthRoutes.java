package fr.expand.project.importdata.api.server;

import static fr.expand.project.importdata.api.server.ApiSupport.*;
import static fr.expand.project.importdata.api.server.ModelMapper.*;

import fr.expand.project.importdata.model.Neo4jModelStore;

import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;

import java.util.HashMap;
import java.util.Map;

final class HealthRoutes {
    private HealthRoutes() {}

    static void register(RoutesConfig app) {
        app.get("/api/health", ctx -> ctx.result(health(ctx)));
    }

    static String health(Context ctx) throws Exception {
        ctx.contentType("application/json");
        boolean deep = "true".equalsIgnoreCase(ctx.queryParam("deep"));
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "ok");
        payload.put("api", "ok");
        payload.put("neo4j", "unknown");

        if (deep) {
            try (Neo4jModelStore store = new Neo4jModelStore()) {
                store.listModels();
                payload.put("neo4j", "ok");
            } catch (Exception e) {
                payload.put("neo4j", "ko");
                payload.put("neo4jError", "Base de données indisponible");
                payload.put("status", "degraded");
                ctx.status(503);
            }
        }

        return GSON.toJson(payload);
    }
}
