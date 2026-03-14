package fr.expand.project.importdata.api.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static spark.Spark.delete;
import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.put;

import fr.expand.project.importdata.access.AccessContext;
import fr.expand.project.importdata.model.ModelManager;
import fr.expand.project.importdata.model.Neo4jModelStore;
import fr.expand.project.importdata.model.generated.DATAMODEL;

final class ModelRoutes {

    private final ApiSupport apiSupport;
    private final AccessSupport accessSupport;
    private final ModelPayloadMapper modelPayloadMapper;

    ModelRoutes(ApiSupport apiSupport, AccessSupport accessSupport, ModelPayloadMapper modelPayloadMapper) {
        this.apiSupport = apiSupport;
        this.accessSupport = accessSupport;
        this.modelPayloadMapper = modelPayloadMapper;
    }

    void register() {
        get("/api/models", (request, response) -> {
            response.type("application/json");
            AccessContext context = accessSupport.resolveAccessContext(request, response);
            if (context == null) {
                return apiSupport.error(response, 401, "Utilisateur inconnu");
            }

            try (Neo4jModelStore store = new Neo4jModelStore()) {
                List<Map<String, Object>> models = store.listModels();
                if (context.isPlatformAdmin()) {
                    return apiSupport.toJson(models);
                }

                List<Map<String, Object>> filtered = new ArrayList<>();
                for (Map<String, Object> model : models) {
                    String modelKey = apiSupport.getString(model.get("key"));
                    if (modelKey != null && context.canViewModel(modelKey)) {
                        filtered.add(model);
                    }
                }
                return apiSupport.toJson(filtered);
            }
        });

        get("/api/models/:key", (request, response) -> {
            response.type("application/json");
            AccessContext context = accessSupport.resolveAccessContext(request, response);
            if (context == null) {
                return apiSupport.error(response, 401, "Utilisateur inconnu");
            }

            String modelKey = request.params("key");
            if (modelKey == null || modelKey.isBlank()) {
                return apiSupport.error(response, 400, "modelKey manquant");
            }
            if (!context.canViewModel(modelKey)) {
                return apiSupport.error(response, 403, "Accès au modèle refusé");
            }

            try (Neo4jModelStore store = new Neo4jModelStore()) {
                DATAMODEL model = store.loadModelByKey(modelKey);
                if (model == null) {
                    return apiSupport.error(response, 404, "Modèle introuvable");
                }
                return apiSupport.toJson(modelPayloadMapper.buildModelDetails(modelKey, model));
            }
        });

        get("/api/models/:key/xml", (request, response) -> {
            AccessContext context = accessSupport.resolveAccessContext(request, response);
            if (context == null) {
                response.type("application/json");
                return apiSupport.error(response, 401, "Utilisateur inconnu");
            }

            String modelKey = request.params("key");
            if (modelKey == null || modelKey.isBlank()) {
                response.type("application/json");
                return apiSupport.error(response, 400, "modelKey manquant");
            }
            if (!context.isPortalModelAdmin()) {
                response.type("application/json");
                return apiSupport.error(response, 403, "Accès portail administration refusé");
            }
            if (!context.canViewModel(modelKey)) {
                response.type("application/json");
                return apiSupport.error(response, 403, "Accès au modèle refusé");
            }

            try (Neo4jModelStore store = new Neo4jModelStore()) {
                String xml = store.loadModelXmlByKey(modelKey);
                if (xml == null || xml.isBlank()) {
                    response.type("application/json");
                    return apiSupport.error(response, 404, "Modèle introuvable");
                }
                response.type("application/xml");
                return xml;
            }
        });

        put("/api/models/:key", (request, response) -> {
            response.type("application/json");
            AccessContext context = accessSupport.resolveAccessContext(request, response);
            if (context == null) {
                return apiSupport.error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isPortalModelAdmin()) {
                return apiSupport.error(response, 403, "Accès portail administration refusé");
            }

            String modelKey = request.params("key");
            if (modelKey == null || modelKey.isBlank()) {
                return apiSupport.error(response, 400, "modelKey manquant");
            }
            if (!context.canViewModel(modelKey)) {
                return apiSupport.error(response, 403, "Accès au modèle refusé");
            }

            String xml = request.body();
            if (xml == null || xml.isBlank()) {
                return apiSupport.error(response, 400, "XML du modèle manquant");
            }

            try {
                ModelManager modelManager = ModelManager.getInstance();
                DATAMODEL model = modelManager.loadModelFromXml(xml);
                try (Neo4jModelStore store = new Neo4jModelStore()) {
                    String newKey = store.storeModel(model, xml);
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("key", newKey);
                    payload.put("requestedKey", modelKey);
                    payload.put("name", model.getNAME());
                    payload.put("version", model.getVERSION() == null ? "" : model.getVERSION());
                    payload.put("renamed", !modelKey.equals(newKey));
                    return apiSupport.toJson(payload);
                }
            } catch (Exception e) {
                return apiSupport.error(response, 500, "Erreur lors de la mise à jour du modèle: " + e.getMessage());
            }
        });

        post("/api/models", (request, response) -> {
            response.type("application/json");
            AccessContext context = accessSupport.resolveAccessContext(request, response);
            if (context == null) {
                return apiSupport.error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isPortalModelAdmin()) {
                return apiSupport.error(response, 403, "Accès portail administration refusé");
            }

            try {
                String xml = apiSupport.readMultipartText(request.raw(), "modelFile");
                if (xml == null || xml.isBlank()) {
                    return apiSupport.error(response, 400, "Fichier modèle manquant");
                }

                ModelManager modelManager = ModelManager.getInstance();
                DATAMODEL model = modelManager.loadModelFromXml(xml);
                try (Neo4jModelStore store = new Neo4jModelStore()) {
                    String modelKey = store.storeModel(model, xml);
                    return apiSupport.toJson(modelPayloadMapper.buildModelDetails(modelKey, model));
                }
            } catch (Exception e) {
                return apiSupport.error(response, 500, "Erreur lors du chargement du modèle: " + e.getMessage());
            }
        });

        delete("/api/models/:key", (request, response) -> {
            response.type("application/json");
            AccessContext context = accessSupport.resolveAccessContext(request, response);
            if (context == null) {
                return apiSupport.error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isPortalModelAdmin()) {
                return apiSupport.error(response, 403, "Accès portail administration refusé");
            }

            String modelKey = request.params("key");
            if (modelKey == null || modelKey.isBlank()) {
                return apiSupport.error(response, 400, "modelKey manquant");
            }
            if (!context.canViewModel(modelKey)) {
                return apiSupport.error(response, 403, "Accès au modèle refusé");
            }

            try (Neo4jModelStore store = new Neo4jModelStore()) {
                store.deleteModelAndDataByKey(modelKey);
            }
            return apiSupport.toJson(Map.of("status", "deleted", "modelKey", modelKey));
        });
    }
}
