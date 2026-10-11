package fr.expand.project.importdata.api.server;

import static fr.expand.project.importdata.api.server.ApiSupport.*;

import fr.expand.project.importdata.access.AccessContext;
import fr.expand.project.importdata.audit.AuditActor;
import fr.expand.project.importdata.workflow.WorkflowEngine;

import io.javalin.config.RoutesConfig;
import io.javalin.http.*;

import java.util.NoSuchElementException;

final class WorkflowObjectRoutes {
    private WorkflowObjectRoutes() {}

    static void register(RoutesConfig routes) {
        routes.get(
                "/api/objects/{id}/workflow",
                ctx -> {
                    String key = ctx.queryParam("modelKey");
                    var access = authorize(ctx, key, false);
                    try {
                        ctx.result(
                                GSON.toJson(
                                        new WorkflowEngine()
                                                .loadObjectWorkflow(
                                                        key,
                                                        objectId(ctx),
                                                        access.canTransition(key))));
                    } catch (NoSuchElementException e) {
                        throw new NotFoundResponse(e.getMessage());
                    }
                });
        routes.post(
                "/api/objects/{id}/workflow/transitions",
                ctx -> {
                    var payload = readJsonBody(ctx.body());
                    if (payload == null) throw new BadRequestResponse("Corps JSON objet requis");
                    String key = getString(payload.get("modelKey"));
                    var access = authorize(ctx, key, true);
                    Long revision = getLong(payload.get("expectedRevision"));
                    if (revision == null || revision < 0)
                        throw new BadRequestResponse("Révision attendue requise");
                    try {
                        ctx.result(
                                GSON.toJson(
                                        new WorkflowEngine()
                                                .transition(
                                                        key,
                                                        objectId(ctx),
                                                        getString(payload.get("objectUuid")),
                                                        getString(payload.get("transitionId")),
                                                        revision,
                                                        AuditActor.from(access))));
                    } catch (NoSuchElementException e) {
                        throw new NotFoundResponse(e.getMessage());
                    }
                });
    }

    private static long objectId(Context ctx) {
        Long id = getLong(ctx.pathParam("id"));
        if (id == null || id < 0) throw new BadRequestResponse("Identifiant invalide");
        return id;
    }

    private static AccessContext authorize(Context ctx, String key, boolean mutate) {
        var access = resolveAccessContext(ctx);
        if (access == null) throw new UnauthorizedResponse("Session invalide");
        if (key == null || key.isBlank()) throw new BadRequestResponse("modelKey manquant");
        if (!access.isPortalUser()
                || !access.canReadData(key)
                || (mutate && !access.canTransition(key)))
            throw new ForbiddenResponse("Droits insuffisants pour ce workflow");
        return access;
    }
}
