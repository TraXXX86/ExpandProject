package fr.expand.project.importdata.api.server;

import static fr.expand.project.importdata.api.server.ApiSupport.*;

import fr.expand.project.importdata.data.GraphInsightsStore;

import io.javalin.config.RoutesConfig;
import io.javalin.http.*;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;

final class GraphInsightsRoutes {
    private GraphInsightsRoutes() {}

    static void register(RoutesConfig routes) {
        routes.get(
                "/api/graph/paths",
                ctx -> {
                    String key = authorize(ctx);
                    Long from = getLong(ctx.queryParam("fromId"));
                    Long to = getLong(ctx.queryParam("toId"));
                    if (from == null || to == null || from < 0 || to < 0)
                        throw new BadRequestResponse("fromId/toId invalides");
                    String direction = ctx.queryParam("respectDirection");
                    if (direction != null && !Set.of("true", "false").contains(direction))
                        throw new BadRequestResponse("respectDirection invalide");
                    String rawTypes = ctx.queryParam("types");
                    if (rawTypes != null && rawTypes.length() > 2048)
                        throw new BadRequestResponse("Liste de types trop longue");
                    Set<String> types = new LinkedHashSet<>();
                    if (rawTypes != null && !rawTypes.isBlank()) {
                        Arrays.stream(rawTypes.split(",", -1))
                                .map(String::trim)
                                .forEach(types::add);
                        if (types.contains("")) throw new BadRequestResponse("Type de lien vide");
                    }
                    try {
                        ctx.result(
                                GSON.toJson(
                                        new GraphInsightsStore()
                                                .paths(
                                                        key,
                                                        from,
                                                        to,
                                                        pageArgument(ctx, "maxDepth", 4, 1, 6),
                                                        types,
                                                        !"false".equals(direction),
                                                        loadModelContext(key))));
                    } catch (NoSuchElementException e) {
                        throw new NotFoundResponse(e.getMessage());
                    }
                });
        routes.get(
                "/api/quality",
                ctx -> {
                    String key = authorize(ctx);
                    ctx.result(
                            GSON.toJson(
                                    new GraphInsightsStore().quality(key, loadModelContext(key))));
                });
    }

    private static String authorize(Context ctx) {
        var access = resolveAccessContext(ctx);
        if (access == null) throw new UnauthorizedResponse("Session invalide");
        String key = ctx.queryParam("modelKey");
        if (key == null || key.isBlank()) throw new BadRequestResponse("modelKey manquant");
        if (!access.isPortalUser() || !access.canReadData(key))
            throw new ForbiddenResponse("Droit READ refusé pour ce modèle");
        return key;
    }
}
