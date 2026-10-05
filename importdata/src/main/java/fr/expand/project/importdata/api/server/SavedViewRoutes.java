package fr.expand.project.importdata.api.server;

import static fr.expand.project.importdata.api.server.ApiSupport.*;

import fr.expand.project.importdata.access.*;
import fr.expand.project.importdata.model.Neo4jModelStore;

import io.javalin.config.RoutesConfig;
import io.javalin.http.*;

import java.util.*;

final class SavedViewRoutes {
    private SavedViewRoutes() {}

    static void register(RoutesConfig routes) {
        routes.get(
                "/api/views",
                ctx -> {
                    String key = ctx.queryParam("modelKey");
                    AccessContext access = authorize(ctx, key);
                    try (SavedViewStore store = new SavedViewStore()) {
                        var rows = store.list(key, access.getUsername());
                        boolean truncated = rows.size() > 500;
                        ctx.result(
                                GSON.toJson(
                                        Map.of(
                                                "items",
                                                truncated ? rows.subList(0, 500) : rows,
                                                "truncated",
                                                truncated)));
                    }
                });
        routes.post(
                "/api/views",
                ctx -> {
                    var body = body(ctx);
                    String key = getString(body.get("modelKey"));
                    AccessContext access = authorize(ctx, key);
                    try (SavedViewStore store = new SavedViewStore()) {
                        ctx.status(201)
                                .result(
                                        GSON.toJson(
                                                store.create(
                                                        key,
                                                        access.getUsername(),
                                                        getString(body.get("name")),
                                                        shared(body),
                                                        state(body))));
                    }
                });
        routes.put("/api/views/{id}", ctx -> mutate(ctx, false));
        routes.delete("/api/views/{id}", ctx -> mutate(ctx, true));
    }

    private static void mutate(Context ctx, boolean delete) {
        AccessContext access = resolveAccessContext(ctx);
        if (access == null) throw new UnauthorizedResponse("Session invalide");
        try (SavedViewStore store = new SavedViewStore()) {
            var current = store.find(ctx.pathParam("id"), access.getUsername());
            if (current == null) throw new NotFoundResponse("Vue introuvable");
            authorize(ctx, (String) current.get("modelKey"));
            if (!access.getUsername().equals(current.get("owner")))
                throw new ForbiddenResponse("Seul le propriétaire peut modifier cette vue");
            if (delete) {
                if (!store.delete(ctx.pathParam("id"), access.getUsername()))
                    throw new NotFoundResponse("Vue introuvable");
                ctx.result(GSON.toJson(Map.of("status", "deleted")));
            } else {
                var body = body(ctx);
                if (body.containsKey("modelKey")
                        && !Objects.equals(body.get("modelKey"), current.get("modelKey")))
                    throw new BadRequestResponse("Le modèle de la vue ne peut pas être changé");
                var row =
                        store.update(
                                ctx.pathParam("id"),
                                access.getUsername(),
                                getString(body.get("name")),
                                shared(body),
                                state(body));
                if (row == null) throw new NotFoundResponse("Vue introuvable");
                ctx.result(GSON.toJson(row));
            }
        }
    }

    private static AccessContext authorize(Context ctx, String key) {
        AccessContext access = resolveAccessContext(ctx);
        if (access == null) throw new UnauthorizedResponse("Session invalide");
        if (key == null || key.isBlank()) throw new BadRequestResponse("Modèle requis");
        if (!access.isPortalUser() || !access.canReadData(key))
            throw new ForbiddenResponse("Lecture refusée pour ce modèle");
        try (Neo4jModelStore store = new Neo4jModelStore()) {
            if (store.loadModelXmlByKey(key) == null)
                throw new NotFoundResponse("Modèle introuvable");
        }
        return access;
    }

    private static Map<String, Object> body(Context ctx) {
        var body = readJsonBody(ctx.body());
        if (body == null) throw new BadRequestResponse("Corps JSON objet requis");
        return body;
    }

    private static boolean shared(Map<String, Object> body) {
        Object value = body.getOrDefault("shared", false);
        if (!(value instanceof Boolean flag)) throw new BadRequestResponse("Partage invalide");
        return flag;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> state(Map<String, Object> body) {
        if (!(body.get("state") instanceof Map<?, ?> map))
            throw new BadRequestResponse("Configuration de vue requise");
        return (Map<String, Object>) map;
    }
}
