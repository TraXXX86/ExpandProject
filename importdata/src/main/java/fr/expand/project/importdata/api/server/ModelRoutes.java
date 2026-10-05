package fr.expand.project.importdata.api.server;

import static fr.expand.project.importdata.api.server.ApiSupport.*;
import static fr.expand.project.importdata.api.server.ModelMapper.*;

import fr.expand.project.importdata.access.AccessContext;
import fr.expand.project.importdata.access.AccessControlStore;
import fr.expand.project.importdata.model.ModelManager;
import fr.expand.project.importdata.model.Neo4jModelStore;
import fr.expand.project.importdata.model.generated.DATAMODEL;

import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ModelRoutes {
    private ModelRoutes() {}

    static void register(RoutesConfig app) {
        app.get("/api/models", ctx -> ctx.result(listModels(ctx)));
        app.get("/api/models/{key}", ctx -> ctx.result(getModel(ctx)));
        app.get("/api/models/{key}/xml", ctx -> ctx.result(getModelXml(ctx)));
        app.put("/api/models/{key}", ctx -> ctx.result(updateModel(ctx)));
        app.post("/api/models", ctx -> ctx.result(createModel(ctx)));
        app.delete("/api/models/{key}", ctx -> ctx.result(deleteModel(ctx)));
    }

    static String listModels(Context ctx) throws Exception {
        ctx.contentType("application/json");
        AccessContext context = resolveAccessContext(ctx);
        if (context == null) {
            return error(ctx, 401, "Utilisateur inconnu");
        }
        try (Neo4jModelStore store = new Neo4jModelStore()) {
            List<Map<String, Object>> models = store.listModels();
            if (context.isPlatformAdmin()) {
                return GSON.toJson(models);
            }
            List<Map<String, Object>> filtered = new ArrayList<>();
            for (Map<String, Object> model : models) {
                String modelKey = getString(model.get("key"));
                if (modelKey != null && context.canViewModel(modelKey)) {
                    filtered.add(model);
                }
            }
            return GSON.toJson(filtered);
        }
    }

    static String getModel(Context ctx) throws Exception {
        ctx.contentType("application/json");
        AccessContext context = resolveAccessContext(ctx);
        if (context == null) {
            return error(ctx, 401, "Utilisateur inconnu");
        }
        String modelKey = ctx.pathParam("key");
        if (modelKey == null || modelKey.isBlank()) {
            return error(ctx, 400, "modelKey manquant");
        }
        if (!context.canViewModel(modelKey)) {
            return error(ctx, 403, "Accès au modèle refusé");
        }

        try (Neo4jModelStore store = new Neo4jModelStore()) {
            DATAMODEL model = store.loadModelByKey(modelKey);
            if (model == null) {
                return error(ctx, 404, "Modèle introuvable");
            }
            return GSON.toJson(buildModelDetails(modelKey, model));
        }
    }

    static String getModelXml(Context ctx) throws Exception {
        AccessContext context = resolveAccessContext(ctx);
        if (context == null) {
            ctx.contentType("application/json");
            return error(ctx, 401, "Utilisateur inconnu");
        }
        String modelKey = ctx.pathParam("key");
        if (modelKey == null || modelKey.isBlank()) {
            ctx.contentType("application/json");
            return error(ctx, 400, "modelKey manquant");
        }
        if (!context.isPortalModelAdmin()) {
            ctx.contentType("application/json");
            return error(ctx, 403, "Accès portail administration refusé");
        }
        if (!context.canViewModel(modelKey)) {
            ctx.contentType("application/json");
            return error(ctx, 403, "Accès au modèle refusé");
        }

        try (Neo4jModelStore store = new Neo4jModelStore()) {
            String xml = store.loadModelXmlByKey(modelKey);
            if (xml == null || xml.isBlank()) {
                ctx.contentType("application/json");
                return error(ctx, 404, "Modèle introuvable");
            }
            ctx.contentType("application/xml");
            return xml;
        }
    }

    static String updateModel(Context ctx) throws Exception {
        ctx.contentType("application/json");
        AccessContext context = resolveAccessContext(ctx);
        if (context == null) {
            return error(ctx, 401, "Utilisateur inconnu");
        }
        if (!context.isPortalModelAdmin()) {
            return error(ctx, 403, "Accès portail administration refusé");
        }
        String modelKey = ctx.pathParam("key");
        if (modelKey == null || modelKey.isBlank()) {
            return error(ctx, 400, "modelKey manquant");
        }
        if (!context.canUpdateData(modelKey)) {
            return error(ctx, 403, "Accès au modèle refusé");
        }
        String xml = ctx.body();
        if (xml == null || xml.isBlank()) {
            return error(ctx, 400, "XML du modèle manquant");
        }

        try {
            ModelManager modelManager = new ModelManager();
            DATAMODEL model = modelManager.loadModelFromXml(xml);
            try (Neo4jModelStore store = new Neo4jModelStore()) {
                String newKey = store.updateModel(modelKey, model, xml);
                Map<String, Object> payload = new HashMap<>();
                payload.put("key", newKey);
                payload.put("requestedKey", modelKey);
                payload.put("name", model.getNAME());
                payload.put("version", model.getVERSION() == null ? "" : model.getVERSION());
                payload.put("renamed", !modelKey.equals(newKey));
                return GSON.toJson(payload);
            }
        } catch (Exception e) {
            throw e;
        }
    }

    static String createModel(Context ctx) throws Exception {
        ctx.contentType("application/json");
        AccessContext context = resolveAccessContext(ctx);
        if (context == null) {
            return error(ctx, 401, "Utilisateur inconnu");
        }
        if (!context.isPortalModelAdmin()) {
            return error(ctx, 403, "Accès portail administration refusé");
        }
        try {
            String xml = readMultipartText(ctx, "modelFile");
            if (xml == null || xml.isBlank()) {
                return error(ctx, 400, "Fichier modèle manquant");
            }

            ModelManager modelManager = new ModelManager();
            DATAMODEL model = modelManager.loadModelFromXml(xml);
            try (Neo4jModelStore store = new Neo4jModelStore()) {
                String modelKey = store.createModel(model, xml);
                if (!context.isPlatformAdmin()) {
                    try (AccessControlStore access = new AccessControlStore()) {
                        var permissions =
                                new ArrayList<>(access.listModelPermissions(context.getUsername()));
                        permissions.removeIf(row -> modelKey.equals(row.get("modelKey")));
                        permissions.add(
                                Map.of(
                                        "modelKey",
                                        modelKey,
                                        "visible",
                                        true,
                                        "canRead",
                                        true,
                                        "canCreate",
                                        true,
                                        "canUpdate",
                                        true,
                                        "canDelete",
                                        true));
                        access.replaceModelPermissions(context.getUsername(), permissions);
                    }
                }
                ctx.status(201);
                return GSON.toJson(buildModelDetails(modelKey, model));
            }
        } catch (Exception e) {
            throw e;
        }
    }

    static String deleteModel(Context ctx) throws Exception {
        ctx.contentType("application/json");
        AccessContext context = resolveAccessContext(ctx);
        if (context == null) {
            return error(ctx, 401, "Utilisateur inconnu");
        }
        if (!context.isPortalModelAdmin()) {
            return error(ctx, 403, "Accès portail administration refusé");
        }
        String modelKey = ctx.pathParam("key");
        if (modelKey == null || modelKey.isBlank()) {
            return error(ctx, 400, "modelKey manquant");
        }
        if (!context.canDeleteData(modelKey)) {
            return error(ctx, 403, "Accès au modèle refusé");
        }
        try (Neo4jModelStore store = new Neo4jModelStore()) {
            store.deleteModelAndDataByKey(modelKey);
        }
        return GSON.toJson(Map.of("status", "deleted", "modelKey", modelKey));
    }
}
