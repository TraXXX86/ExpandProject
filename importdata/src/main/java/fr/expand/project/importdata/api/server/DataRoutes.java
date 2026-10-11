package fr.expand.project.importdata.api.server;

import static fr.expand.project.importdata.api.server.ApiSupport.*;

import fr.expand.project.importdata.access.AccessContext;
import fr.expand.project.importdata.api.impl.ModelBasedImportAPI;
import fr.expand.project.importdata.audit.AuditActor;
import fr.expand.project.importdata.data.Neo4jDataStore;
import fr.expand.project.importdata.dto.generated.ATTRIBUTE;
import fr.expand.project.importdata.dto.generated.DATAS;
import fr.expand.project.importdata.dto.generated.LINK;
import fr.expand.project.importdata.dto.generated.OBJECT;
import fr.expand.project.importdata.model.ModelManager;
import fr.expand.project.importdata.validation.DataValidator;
import fr.expand.project.importdata.validation.ValidationResult;
import fr.expand.project.importdata.xml.XmlSupport;

import io.javalin.config.RoutesConfig;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.UnauthorizedResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class DataRoutes {
    private DataRoutes() {}

    static void register(RoutesConfig routes) {
        routes.post("/api/data", ctx -> ctx.result(importData(ctx)));
        routes.get("/api/data", ctx -> ctx.result(listData(ctx)));
        routes.post("/api/objects", ctx -> ctx.result(createObject(ctx)));
        routes.put("/api/objects/{id}", ctx -> ctx.result(updateObject(ctx)));
        routes.delete("/api/objects/{id}", ctx -> ctx.result(deleteEntity(ctx, false)));
        routes.get("/api/objects/{id}/neighbors", ctx -> ctx.result(neighbors(ctx)));
        routes.post("/api/links", ctx -> ctx.result(createLink(ctx)));
        routes.put("/api/links/{id}", ctx -> ctx.result(updateLink(ctx)));
        routes.delete("/api/links/{id}", ctx -> ctx.result(deleteEntity(ctx, true)));
    }

    private static void authorize(Context ctx, String key, String action) {
        AccessContext access = resolveAccessContext(ctx);
        if (access == null) throw new UnauthorizedResponse("Session invalide");
        if (key == null || key.isBlank()) throw new BadRequestResponse("modelKey manquant");
        boolean allowed =
                access.isPortalUser()
                        && switch (action) {
                            case "read" -> access.canReadData(key);
                            case "create" -> access.canCreateData(key);
                            case "update" -> access.canUpdateData(key);
                            case "delete" -> access.canDeleteData(key);
                            default -> false;
                        };
        if (!allowed)
            throw new ForbiddenResponse("Droit " + action.toUpperCase() + " refusé pour ce modèle");
    }

    private static Map<String, Object> body(Context ctx) {
        Map<String, Object> payload = readJsonBody(ctx.body());
        if (payload == null) throw new BadRequestResponse("Corps JSON objet requis");
        return payload;
    }

    private static long entityId(Context ctx) {
        Long id = getLong(ctx.pathParam("id"));
        if (id == null || id < 0) throw new BadRequestResponse("Identifiant invalide");
        return id;
    }

    private static String validationError(Context ctx, ValidationResult validation) {
        ctx.status(400);
        return GSON.toJson(
                Map.of(
                        "valid",
                        false,
                        "errors",
                        validation.getErrors(),
                        "warnings",
                        validation.getWarnings()));
    }

    private static Map<String, Object> attributeMap(List<? extends ATTRIBUTE> attributes) {
        Map<String, Object> result = new HashMap<>();
        for (ATTRIBUTE attr : attributes) result.put(attr.getKEY(), attr.getVALUE());
        return result;
    }

    static String importData(Context ctx) throws Exception {
        String key = ctx.formParam("modelKey");
        authorize(ctx, key, "create");
        boolean validateOnly = Boolean.parseBoolean(ctx.formParam("validateOnly"));
        String xml = readMultipartText(ctx, "dataFile");
        if (xml == null || xml.isBlank())
            throw new BadRequestResponse("Fichier de données manquant");
        ModelManager manager = loadModelContext(key);
        DATAS data = XmlSupport.parseData(xml);
        try (ModelBasedImportAPI importer =
                new ModelBasedImportAPI(manager, AuditActor.from(resolveAccessContext(ctx)))) {
            ValidationResult result = importer.importData(data, validateOnly, key);
            if (!result.isValid()) ctx.status(400);
            return GSON.toJson(
                    Map.of(
                            "modelKey",
                            key,
                            "valid",
                            result.isValid(),
                            "validateOnly",
                            validateOnly,
                            "imported",
                            result.isValid() && !validateOnly,
                            "errors",
                            result.getErrors(),
                            "warnings",
                            result.getWarnings(),
                            "objectCount",
                            countObjects(data),
                            "linkCount",
                            countLinks(data)));
        }
    }

    static String listData(Context ctx) {
        String key = ctx.queryParam("modelKey");
        authorize(ctx, key, "read");
        int offset = pageArgument(ctx, "offset", 0, 0, Integer.MAX_VALUE);
        int limit = pageArgument(ctx, "limit", 100, 1, 500);
        String q = ctx.queryParam("q");
        if (q != null && q.length() > 512)
            throw new BadRequestResponse("Recherche limitée à 512 caractères");
        String searchMode = ctx.queryParam("searchMode");
        if (searchMode == null) searchMode = "contains";
        if (!java.util.Set.of("contains", "fulltext").contains(searchMode))
            throw new BadRequestResponse("searchMode invalide");
        try (Neo4jDataStore store =
                new Neo4jDataStore(null, AuditActor.from(resolveAccessContext(ctx)))) {
            return GSON.toJson(
                    store.loadDataPage(
                            key,
                            offset,
                            limit,
                            q,
                            ctx.queryParam("type"),
                            searchMode,
                            ctx.queryParam("workflowStatus"),
                            ctx.queryParam("workflowId")));
        }
    }

    static String neighbors(Context ctx) {
        String key = ctx.queryParam("modelKey");
        authorize(ctx, key, "read");
        long id = entityId(ctx);
        try (Neo4jDataStore store =
                new Neo4jDataStore(null, AuditActor.from(resolveAccessContext(ctx)))) {
            if (store.loadObjectById(key, id) == null) return error(ctx, 404, "Objet introuvable");
            return GSON.toJson(store.neighbors(key, id, pageArgument(ctx, "limit", 100, 1, 500)));
        }
    }

    static String createObject(Context ctx) throws Exception {
        Map<String, Object> payload = body(ctx);
        String key = getString(payload.get("modelKey"));
        authorize(ctx, key, "create");
        String type = getString(payload.get("type"));
        Integer externalId = getInt(payload.get("id"));
        if (payload.get("id") != null && (externalId == null || externalId < 0))
            throw new BadRequestResponse("id externe invalide");
        OBJECT object = new OBJECT();
        object.setID(externalId == null ? 0 : externalId);
        object.setTYPE(type);
        object.getATTRIBUTE().addAll(readAttributes(payload.get("attributes")));
        ModelManager manager = loadModelContext(key);
        ValidationResult validation = new DataValidator(manager).validateObjectAttributes(object);
        if (!validation.isValid()) return validationError(ctx, validation);
        try (Neo4jDataStore store =
                new Neo4jDataStore(
                        manager.getCurrentModelXml(), AuditActor.from(resolveAccessContext(ctx)))) {
            long id =
                    store.createObject(key, type, attributeMap(object.getATTRIBUTE()), externalId);
            ctx.status(201);
            return GSON.toJson(
                    Map.of(
                            "status",
                            "created",
                            "id",
                            id,
                            "type",
                            type,
                            "warnings",
                            validation.getWarnings()));
        }
    }

    static String updateObject(Context ctx) throws Exception {
        long id = entityId(ctx);
        Map<String, Object> payload = body(ctx);
        String key = getString(payload.get("modelKey"));
        authorize(ctx, key, "update");
        ModelManager manager = loadModelContext(key);
        try (Neo4jDataStore store =
                new Neo4jDataStore(
                        manager.getCurrentModelXml(), AuditActor.from(resolveAccessContext(ctx)))) {
            Map<String, Object> current = store.loadObjectById(key, id);
            if (current == null) return error(ctx, 404, "Objet introuvable");
            String type = getString(current.get("type"));
            String desiredType = getString(payload.get("type"));
            if (desiredType != null && !desiredType.equals(type))
                throw new BadRequestResponse("Le type ne peut pas être modifié");
            // Validate incoming duplicate/reserved keys before merging the patch.
            var incoming = readAttributes(payload.get("attributes"));
            validateAttributeKeys(incoming);
            Map<String, Object> merged = attributesToMap(readAttributes(current.get("attributes")));
            merged.putAll(attributesToMap(incoming));
            OBJECT object = new OBJECT();
            object.setID(0); // validation does not depend on database internal IDs
            object.setTYPE(type);
            object.getATTRIBUTE().addAll(readAttributesFromMap(merged));
            ValidationResult validation =
                    new DataValidator(manager).validateObjectAttributes(object);
            if (!validation.isValid()) return validationError(ctx, validation);
            if (!store.updateObject(key, id, attributeMap(object.getATTRIBUTE())))
                return error(ctx, 404, "Objet introuvable");
            return GSON.toJson(
                    Map.of(
                            "status",
                            "updated",
                            "id",
                            id,
                            "type",
                            type,
                            "warnings",
                            validation.getWarnings()));
        }
    }

    static String createLink(Context ctx) throws Exception {
        Map<String, Object> payload = body(ctx);
        String key = getString(payload.get("modelKey"));
        authorize(ctx, key, "create");
        Long from = getLong(payload.get("fromId"));
        Long to = getLong(payload.get("toId"));
        if (from == null || to == null || from < 0 || to < 0)
            throw new BadRequestResponse("fromId/toId invalides");
        String type = getString(payload.get("type"));
        ModelManager manager = loadModelContext(key);
        LINK link = new LINK();
        link.setTYPE(type);
        link.getATTRIBUTE().addAll(readAttributes(payload.get("attributes")));
        ValidationResult validation = new DataValidator(manager).validateLinkAttributes(link);
        if (!validation.isValid()) return validationError(ctx, validation);
        var definition = manager.getLinkType(type);
        try (Neo4jDataStore store =
                new Neo4jDataStore(
                        manager.getCurrentModelXml(), AuditActor.from(resolveAccessContext(ctx)))) {
            var source = store.loadObjectById(key, from);
            var target = store.loadObjectById(key, to);
            if (source == null || target == null)
                return error(ctx, 404, "Objet source ou cible introuvable");
            if (!isLinkTypeAllowed(manager, definition, getString(source.get("type")), true)
                    || !isLinkTypeAllowed(
                            manager, definition, getString(target.get("type")), false))
                throw new BadRequestResponse("Types source/cible non autorisés pour ce lien");
            long id =
                    store.createLink(
                            key,
                            from,
                            to,
                            type,
                            definition.isDIRECTED(),
                            attributeMap(link.getATTRIBUTE()));
            ctx.status(201);
            return GSON.toJson(
                    Map.of(
                            "status", "created", "id", id, "type", type, "fromId", from, "toId",
                            to));
        }
    }

    static String updateLink(Context ctx) throws Exception {
        String id = ctx.pathParam("id");
        Long numericId = getLong(id);
        if (numericId != null && numericId < 0)
            throw new BadRequestResponse("Identifiant invalide");
        Map<String, Object> payload = body(ctx);
        String key = getString(payload.get("modelKey"));
        authorize(ctx, key, "update");
        ModelManager manager = loadModelContext(key);
        try (Neo4jDataStore store =
                new Neo4jDataStore(
                        manager.getCurrentModelXml(), AuditActor.from(resolveAccessContext(ctx)))) {
            var current =
                    numericId == null
                            ? store.loadLinkByUuid(key, id)
                            : store.loadLinkById(key, numericId);
            if (current == null) return error(ctx, 404, "Lien introuvable");
            String type = getString(current.get("type"));
            if (payload.get("type") != null && !type.equals(getString(payload.get("type"))))
                throw new BadRequestResponse("Le type du lien ne peut pas être modifié");
            var incoming = readAttributes(payload.get("attributes"));
            validateAttributeKeys(incoming);
            Map<String, Object> merged = attributesToMap(readAttributes(current.get("attributes")));
            merged.putAll(attributesToMap(incoming));
            LINK link = new LINK();
            link.setTYPE(type);
            link.getATTRIBUTE().addAll(readAttributesFromMap(merged));
            ValidationResult validation = new DataValidator(manager).validateLinkAttributes(link);
            if (!validation.isValid()) return validationError(ctx, validation);
            boolean updated =
                    numericId == null
                            ? store.updateLinkByUuid(key, id, attributeMap(link.getATTRIBUTE()))
                            : store.updateLink(key, numericId, attributeMap(link.getATTRIBUTE()));
            if (!updated) return error(ctx, 404, "Lien introuvable");
            return GSON.toJson(
                    Map.of("status", "updated", "id", id, "warnings", validation.getWarnings()));
        }
    }

    static String deleteEntity(Context ctx, boolean link) {
        String rawId = ctx.pathParam("id");
        Long numericId = getLong(rawId);
        if (!link || numericId != null) numericId = entityId(ctx);
        String key = ctx.queryParam("modelKey");
        if (key == null && !ctx.body().isBlank()) key = getString(body(ctx).get("modelKey"));
        authorize(ctx, key, "delete");
        try (Neo4jDataStore store =
                new Neo4jDataStore(null, AuditActor.from(resolveAccessContext(ctx)))) {
            boolean deleted =
                    link
                            ? (numericId == null
                                    ? store.deleteLinkByUuid(key, rawId)
                                    : store.deleteLink(key, numericId))
                            : store.deleteObject(key, numericId);
            if (!deleted) return error(ctx, 404, link ? "Lien introuvable" : "Objet introuvable");
            return GSON.toJson(
                    Map.of("status", "deleted", "id", numericId == null ? rawId : numericId));
        }
    }

    private static void validateAttributeKeys(List<? extends ATTRIBUTE> attributes) {
        var keys = new java.util.HashSet<String>();
        for (ATTRIBUTE attr : attributes) {
            String key = attr.getKEY();
            if (key == null || key.isBlank() || !keys.add(key))
                throw new BadRequestResponse("Clé d'attribut absente ou dupliquée");
            fr.expand.project.importdata.util.CypherUtils.validateAttributeKey(key);
        }
    }
}
