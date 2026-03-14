package fr.expand.project.importdata.api.server;

import java.util.HashMap;
import java.util.Map;

import static spark.Spark.get;

import fr.expand.project.importdata.model.Neo4jModelStore;

final class HealthRoutes {

    private final ApiSupport apiSupport;

    HealthRoutes(ApiSupport apiSupport) {
        this.apiSupport = apiSupport;
    }

    void register() {
        get("/api/health", (request, response) -> {
            response.type("application/json");
            boolean deep = "true".equalsIgnoreCase(request.queryParams("deep"));
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
                    payload.put("neo4jError", e.getMessage());
                }
            }

            return apiSupport.toJson(payload);
        });
    }
}
