package fr.expand.project.importdata.api.server;

import static fr.expand.project.importdata.api.server.ApiSupport.*;

import fr.expand.project.importdata.access.AccessContext;
import fr.expand.project.importdata.audit.AuditActor;
import fr.expand.project.importdata.imports.*;

import io.javalin.config.RoutesConfig;
import io.javalin.http.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

final class ImportWorkflowRoutes {
    private ImportWorkflowRoutes() {}

    static void register(RoutesConfig routes) {
        routes.post("/api/imports/inspect", ctx -> ctx.result(inspect(ctx)));
        routes.post("/api/imports/preview", ctx -> ctx.result(workflow(ctx, false)));
        routes.post("/api/imports/commit", ctx -> ctx.result(workflow(ctx, true)));
    }

    private record Upload(byte[] bytes, String format, String sheet) {}

    private static AccessContext authorize(Context ctx, String key) {
        AccessContext access = resolveAccessContext(ctx);
        if (access == null) throw new UnauthorizedResponse("Session invalide");
        if (key == null || key.isBlank()) throw new BadRequestResponse("modelKey manquant");
        if (!access.isPortalUser() || !access.canReadData(key) || !access.canCreateData(key))
            throw new ForbiddenResponse("Droits de lecture et création requis pour importer");
        return access;
    }

    private static Upload upload(Context ctx) throws java.io.IOException {
        var file = ctx.uploadedFile("file");
        if (file == null) throw new BadRequestResponse("Fichier manquant");
        String format = ctx.formParam("format");
        if (format == null || format.isBlank()) {
            String name = file.filename();
            int dot = name.lastIndexOf('.');
            format = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
        if (!Set.of("csv", "xlsx", "xml").contains(format))
            throw new BadRequestResponse("Formats autorisés : CSV, XLSX, XML");
        byte[] bytes;
        try (var input = file.content()) {
            bytes = input.readNBytes(TabularFile.MAX_BYTES + 1);
        }
        if (bytes.length > TabularFile.MAX_BYTES)
            throw new HttpResponseException(413, "Fichier limité à 10 Mo");
        return new Upload(bytes, format, ctx.formParam("sheet"));
    }

    static String inspect(Context ctx) throws Exception {
        authorize(ctx, ctx.formParam("modelKey"));
        Upload upload = upload(ctx);
        if (upload.format.equals("xml")) {
            var input = ImportInput.xml(new String(upload.bytes, StandardCharsets.UTF_8));
            return GSON.toJson(
                    Map.of(
                            "format",
                            "xml",
                            "columns",
                            List.of(),
                            "sampleRows",
                            List.of(),
                            "sheets",
                            List.of(),
                            "sheet",
                            "",
                            "rowCount",
                            input.entries().size()));
        }
        var table = TabularFile.read(upload.bytes, upload.format, upload.sheet);
        return GSON.toJson(
                Map.of(
                        "format",
                        upload.format,
                        "columns",
                        table.columns(),
                        "sampleRows",
                        table.rows().subList(0, Math.min(10, table.rows().size())),
                        "sheets",
                        table.sheets(),
                        "sheet",
                        table.sheet(),
                        "rowCount",
                        table.rows().size()));
    }

    static String workflow(Context ctx, boolean commit) throws Exception {
        String key = ctx.formParam("modelKey");
        AccessContext access = authorize(ctx, key);
        Upload upload = upload(ctx);
        String mode = ctx.formParam("mode");
        if (mode == null) mode = "create";
        ImportInput input;
        if (upload.format.equals("xml"))
            input = ImportInput.xml(new String(upload.bytes, StandardCharsets.UTF_8));
        else {
            String json = ctx.formParam("mapping");
            if (json == null) throw new BadRequestResponse("Correspondance des attributs requise");
            Map<String, String> mapping = new TreeMap<>();
            try {
                var value = com.google.gson.JsonParser.parseString(json);
                if (!value.isJsonObject()) throw new IllegalArgumentException();
                value.getAsJsonObject()
                        .entrySet()
                        .forEach(
                                e -> {
                                    if (!e.getValue().isJsonPrimitive()
                                            || !e.getValue().getAsJsonPrimitive().isString())
                                        throw new IllegalArgumentException();
                                    mapping.put(e.getKey(), e.getValue().getAsString());
                                });
            } catch (RuntimeException e) {
                throw new BadRequestResponse("Correspondance JSON invalide");
            }
            input =
                    ImportInput.table(
                            TabularFile.read(upload.bytes, upload.format, upload.sheet),
                            ctx.formParam("objectType"),
                            ctx.formParam("idColumn"),
                            mapping);
        }
        var manager = loadModelContext(key);
        var store = new ImportWorkflowStore();
        if (!commit) return GSON.toJson(store.preview(key, manager, input, mode));
        return GSON.toJson(
                store.commit(
                        key,
                        manager,
                        input,
                        mode,
                        ctx.formParam("previewHash"),
                        access.canCreateData(key),
                        access.canUpdateData(key),
                        AuditActor.from(access)));
    }
}
