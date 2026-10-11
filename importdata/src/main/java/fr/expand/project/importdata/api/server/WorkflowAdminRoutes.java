package fr.expand.project.importdata.api.server;

import static fr.expand.project.importdata.api.server.ApiSupport.*;

import fr.expand.project.importdata.access.AccessContext;
import fr.expand.project.importdata.audit.AuditActor;
import fr.expand.project.importdata.workflow.*;

import io.javalin.config.RoutesConfig;
import io.javalin.http.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

final class WorkflowAdminRoutes {
    private WorkflowAdminRoutes() {}

    static void register(RoutesConfig routes) {
        routes.get(
                "/api/workflows",
                ctx -> {
                    String key = ctx.queryParam("modelKey");
                    authorize(ctx, key, false);
                    ctx.result(GSON.toJson(new WorkflowCatalog().list(key)));
                });
        routes.post(
                "/api/workflows",
                ctx -> {
                    String key = ctx.formParam("modelKey");
                    var access = authorize(ctx, key, true);
                    var file = ctx.uploadedFile("workflowFile");
                    if (file == null) throw new BadRequestResponse("workflowFile required");
                    byte[] bytes;
                    try (var input = file.content()) {
                        bytes = input.readNBytes(WorkflowXml.MAX_BYTES + 1);
                    }
                    if (bytes.length > WorkflowXml.MAX_BYTES)
                        throw new HttpResponseException(413, "Workflow XML limited to 1 MiB");
                    ctx.status(201)
                            .result(
                                    GSON.toJson(
                                            new WorkflowCatalog()
                                                    .upload(
                                                            key,
                                                            new String(
                                                                    bytes, StandardCharsets.UTF_8),
                                                            AuditActor.from(access))));
                });
        routes.post("/api/workflows/activation/preview", ctx -> activation(ctx, false));
        routes.post("/api/workflows/activation/commit", ctx -> activation(ctx, true));
        routes.post("/api/workflows/migration/preview", ctx -> migration(ctx, false));
        routes.post("/api/workflows/migration/commit", ctx -> migration(ctx, true));
        routes.post(
                "/api/workflows/deactivate",
                ctx -> {
                    var p = body(ctx);
                    String key = required(p, "modelKey");
                    var access = authorize(ctx, key, true);
                    new WorkflowCatalog()
                            .deactivate(key, strings(p, "objectTypes"), AuditActor.from(access));
                    ctx.result(GSON.toJson(Map.of("ok", true)));
                });
        routes.delete(
                "/api/workflows",
                ctx -> {
                    var p = body(ctx);
                    String key = required(p, "modelKey");
                    var access = authorize(ctx, key, true);
                    new WorkflowCatalog()
                            .delete(
                                    key,
                                    required(p, "id"),
                                    required(p, "version"),
                                    AuditActor.from(access));
                    ctx.result(GSON.toJson(Map.of("ok", true)));
                });
    }

    private static void activation(Context ctx, boolean commit) {
        var p = body(ctx);
        String key = required(p, "modelKey");
        var access = authorize(ctx, key, true);
        ctx.result(
                GSON.toJson(
                        new WorkflowCatalog()
                                .activate(
                                        key,
                                        required(p, "id"),
                                        required(p, "version"),
                                        getString(p.get("previewHash")),
                                        commit,
                                        AuditActor.from(access))));
    }

    private static void migration(Context ctx, boolean commit) {
        var p = body(ctx);
        String key = required(p, "modelKey");
        var access = authorize(ctx, key, true);
        var ids = new ArrayList<Long>();
        if (p.containsKey("objectIds")) {
            if (!(p.get("objectIds") instanceof List<?> values))
                throw new BadRequestResponse("objectIds must be an array");
            for (var v : values) {
                Long id = getLong(v);
                if (id == null || id < 0) throw new BadRequestResponse("Invalid object id");
                ids.add(id);
            }
        }
        var mapping = new TreeMap<String, String>();
        if (p.containsKey("stateMapping")) {
            if (!(p.get("stateMapping") instanceof Map<?, ?> values))
                throw new BadRequestResponse("stateMapping must be an object");
            for (var e : values.entrySet()) {
                if (!(e.getKey() instanceof String) || !(e.getValue() instanceof String))
                    throw new BadRequestResponse("State mappings must be strings");
                mapping.put((String) e.getKey(), (String) e.getValue());
            }
        }
        var request =
                new WorkflowMigrationService.Request(
                        key,
                        required(p, "id"),
                        required(p, "version"),
                        required(p, "mode"),
                        strings(p, "objectTypes"),
                        ids,
                        getString(p.get("sourceId")),
                        getString(p.get("sourceVersion")),
                        mapping,
                        getString(p.get("previewHash")));
        ctx.result(
                GSON.toJson(
                        new WorkflowMigrationService()
                                .execute(request, commit, AuditActor.from(access))));
    }

    private static Map<String, Object> body(Context ctx) {
        var p = readJsonBody(ctx.body());
        if (p == null) throw new BadRequestResponse("JSON object required");
        return p;
    }

    private static String required(Map<String, Object> p, String name) {
        if (!(p.get(name) instanceof String s) || s.isBlank() || s.length() > 512)
            throw new BadRequestResponse(name + " required (maximum 512 characters)");
        return s;
    }

    private static List<String> strings(Map<String, Object> p, String name) {
        if (!p.containsKey(name)) return List.of();
        if (!(p.get(name) instanceof List<?> list))
            throw new BadRequestResponse(name + " must be an array");
        var out = new ArrayList<String>();
        for (var v : list) {
            if (!(v instanceof String s) || s.isBlank())
                throw new BadRequestResponse(name + " must contain strings");
            out.add(s);
        }
        return out;
    }

    private static AccessContext authorize(Context ctx, String key, boolean manage) {
        var access = resolveAccessContext(ctx);
        if (access == null) throw new UnauthorizedResponse("Session invalide");
        if (key == null || key.isBlank() || key.length() > 512)
            throw new BadRequestResponse("modelKey required");
        if (!access.canViewModel(key)) throw new ForbiddenResponse("Model access required");
        if (manage) {
            if (!access.isPortalModelAdmin()
                    || !access.canReadData(key)
                    || !access.canUpdateData(key))
                throw new ForbiddenResponse(
                        "Model administration, read and update permissions required");
        } else if (!access.isPortalModelAdmin()
                && (!access.isPortalUser() || !access.canReadData(key)))
            throw new ForbiddenResponse("Workflow read access required");
        return access;
    }
}
