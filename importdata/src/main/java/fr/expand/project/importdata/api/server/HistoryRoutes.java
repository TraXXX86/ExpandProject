package fr.expand.project.importdata.api.server;

import static fr.expand.project.importdata.api.server.ApiSupport.*;

import fr.expand.project.importdata.audit.AuditTrail;
import fr.expand.project.importdata.model.Neo4jModelStore;

import io.javalin.config.RoutesConfig;
import io.javalin.http.*;

final class HistoryRoutes {
    private HistoryRoutes() {}

    static void register(RoutesConfig routes) {
        routes.get(
                "/api/history",
                ctx -> {
                    var access = resolveAccessContext(ctx);
                    if (access == null) throw new UnauthorizedResponse("Session invalide");
                    String key = ctx.queryParam("modelKey");
                    if (key == null || key.isBlank())
                        throw new BadRequestResponse("modelKey manquant");
                    if (!access.isPortalUser()
                            || !access.canReadData(key)
                            || !access.canViewModel(key))
                        throw new ForbiddenResponse("Lecture de l’historique refusée");
                    try (var store = new Neo4jModelStore()) {
                        if (store.loadModelXmlByKey(key) == null && !access.isPlatformAdmin())
                            throw new NotFoundResponse("Modèle introuvable");
                    }
                    ctx.contentType("application/json")
                            .result(
                                    GSON.toJson(
                                            AuditTrail.list(
                                                    key,
                                                    pageArgument(
                                                            ctx, "offset", 0, 0, Integer.MAX_VALUE),
                                                    pageArgument(ctx, "limit", 50, 1, 100),
                                                    ctx.queryParam("entityType"),
                                                    ctx.queryParam("entityId"),
                                                    ctx.queryParam("action"))));
                });
    }
}
