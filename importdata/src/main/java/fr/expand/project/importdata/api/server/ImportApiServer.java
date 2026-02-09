package fr.expand.project.importdata.api.server;

import static spark.Spark.before;
import static spark.Spark.delete;
import static spark.Spark.get;
import static spark.Spark.options;
import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.put;
import static spark.Spark.awaitInitialization;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import fr.expand.project.importdata.access.AccessContext;
import fr.expand.project.importdata.access.AccessControlStore;
import fr.expand.project.importdata.api.impl.ModelBasedImportAPI;
import fr.expand.project.importdata.data.Neo4jDataStore;
import fr.expand.project.importdata.dao.connectors.impl.CypherConnector;
import fr.expand.project.importdata.dto.DataPackAttribute;
import fr.expand.project.importdata.dto.DataPackObject;
import fr.expand.project.importdata.dto.generated.DATAS;
import fr.expand.project.importdata.dto.generated.OBJECT;
import fr.expand.project.importdata.dto.generated.OBJECTS;
import fr.expand.project.importdata.model.ModelManager;
import fr.expand.project.importdata.model.Neo4jModelStore;
import fr.expand.project.importdata.model.generated.ATTRIBUTEDEFINITION;
import fr.expand.project.importdata.model.generated.ATTRIBUTEGROUP;
import fr.expand.project.importdata.model.generated.ATTRIBUTEREF;
import fr.expand.project.importdata.model.generated.LABEL;
import fr.expand.project.importdata.model.generated.LANGUAGE;
import fr.expand.project.importdata.model.generated.DATAMODEL;
import fr.expand.project.importdata.model.generated.LINKTYPE;
import fr.expand.project.importdata.model.generated.OBJECTTYPE;
import fr.expand.project.importdata.model.generated.TYPEREF;
import fr.expand.project.importdata.validation.DataValidator;
import fr.expand.project.importdata.validation.ValidationResult;

public class ImportApiServer {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        int portValue = DEFAULT_PORT;
        if (args != null && args.length > 0) {
            try {
                portValue = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                portValue = DEFAULT_PORT;
            }
        }
        start(portValue);
    }

    public static void start(int portValue) {
        port(portValue);
        configureCors();
        registerRoutes();
        awaitInitialization();
    }

    private static void configureCors() {
        options("/*", (request, response) -> {
            addCorsHeaders(response);
            return "OK";
        });

        before((request, response) -> addCorsHeaders(response));
    }

    private static void registerRoutes() {
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

            return GSON.toJson(payload);
        });

        post("/api/auth/login", (request, response) -> {
            response.type("application/json");
            Map<String, Object> payload = readJsonBody(request.body());
            if (payload == null) {
                return error(response, 400, "Corps JSON manquant");
            }
            String username = sanitizeUsername(getString(payload.get("username")));
            String password = getString(payload.get("password"));
            if (username == null || username.isBlank() || password == null || password.isBlank()) {
                return error(response, 400, "username/password manquants");
            }

            try (AccessControlStore accessStore = new AccessControlStore()) {
                Map<String, Object> user = accessStore.authenticate(username, password);
                if (user == null) {
                    return error(response, 401, "Authentification invalide");
                }
                Map<String, Object> session = accessStore.createSession(username);
                if (session == null) {
                    return error(response, 500, "Impossible de créer la session");
                }
                AccessContext context = buildAccessContext(accessStore, session);
                if (context == null) {
                    return error(response, 500, "Impossible de charger le profil utilisateur");
                }
                return GSON.toJson(buildAuthPayload(context, accessStore));
            } catch (Exception e) {
                return error(response, 500, "Erreur d'authentification: " + e.getMessage());
            }
        });

        post("/api/auth/logout", (request, response) -> {
            response.type("application/json");
            String token = resolveSessionToken(request);
            if (token == null || token.isBlank()) {
                return GSON.toJson(Map.of("status", "logged-out"));
            }
            try (AccessControlStore accessStore = new AccessControlStore()) {
                accessStore.deleteSession(token);
                return GSON.toJson(Map.of("status", "logged-out"));
            } catch (Exception e) {
                return error(response, 500, "Erreur lors de la déconnexion: " + e.getMessage());
            }
        });

        get("/api/auth/me", (request, response) -> {
            response.type("application/json");
            AccessContext context = resolveAccessContext(request, response);
            if (context == null) {
                return error(response, 401, "Session invalide");
            }
            try (AccessControlStore accessStore = new AccessControlStore()) {
                return GSON.toJson(buildAuthPayload(context, accessStore));
            } catch (Exception e) {
                return error(response, 500, "Erreur lors du chargement de la session: " + e.getMessage());
            }
        });

        post("/api/auth/impersonate", (request, response) -> {
            response.type("application/json");
            AccessContext context = resolveAccessContext(request, response);
            if (context == null) {
                return error(response, 401, "Session invalide");
            }
            if (!context.isActorPlatformAdmin()) {
                return error(response, 403, "Seul un administrateur peut impersonner");
            }
            Map<String, Object> payload = readJsonBody(request.body());
            if (payload == null) {
                return error(response, 400, "Corps JSON manquant");
            }
            String targetUsername = sanitizeUsername(getString(payload.get("username")));
            if (targetUsername == null || targetUsername.isBlank()) {
                return error(response, 400, "username cible manquant");
            }

            try (AccessControlStore accessStore = new AccessControlStore()) {
                boolean ok = accessStore.impersonateSession(context.getSessionToken(), targetUsername);
                if (!ok) {
                    return error(response, 400, "Impersonation impossible");
                }
                Map<String, Object> session = accessStore.loadSession(context.getSessionToken());
                AccessContext refreshedContext = buildAccessContext(accessStore, session);
                if (refreshedContext == null) {
                    return error(response, 500, "Impossible de charger la session impersonée");
                }
                return GSON.toJson(buildAuthPayload(refreshedContext, accessStore));
            } catch (Exception e) {
                return error(response, 500, "Erreur lors de l'impersonation: " + e.getMessage());
            }
        });

        post("/api/auth/impersonate/stop", (request, response) -> {
            response.type("application/json");
            AccessContext context = resolveAccessContext(request, response);
            if (context == null) {
                return error(response, 401, "Session invalide");
            }
            if (!context.isActorPlatformAdmin()) {
                return error(response, 403, "Seul un administrateur peut arrêter l'impersonation");
            }
            try (AccessControlStore accessStore = new AccessControlStore()) {
                boolean ok = accessStore.stopImpersonation(context.getSessionToken());
                if (!ok) {
                    return error(response, 400, "Aucune impersonation active");
                }
                Map<String, Object> session = accessStore.loadSession(context.getSessionToken());
                AccessContext refreshedContext = buildAccessContext(accessStore, session);
                if (refreshedContext == null) {
                    return error(response, 500, "Impossible de charger la session");
                }
                return GSON.toJson(buildAuthPayload(refreshedContext, accessStore));
            } catch (Exception e) {
                return error(response, 500, "Erreur lors de l'arrêt de l'impersonation: " + e.getMessage());
            }
        });

        get("/api/access/me", (request, response) -> {
            response.type("application/json");
            AccessContext context = resolveAccessContext(request, response);
            if (context == null) {
                return error(response, 401, "Session invalide");
            }
            try (AccessControlStore accessStore = new AccessControlStore()) {
                Map<String, Object> payload = accessStore.loadUserAccess(context.getUsername());
                if (payload == null) {
                    return error(response, 404, "Utilisateur introuvable");
                }
                payload.put("auth", buildAuthMeta(context));
                return GSON.toJson(payload);
            }
        });

        get("/api/access/users", (request, response) -> {
            response.type("application/json");
            AccessContext context = resolveAccessContext(request, response);
            if (context == null) {
                return error(response, 401, "Utilisateur inconnu");
            }
            try (AccessControlStore accessStore = new AccessControlStore()) {
                if (context.isActorPlatformAdmin()) {
                    return GSON.toJson(accessStore.listUsers());
                }
                Map<String, Object> self = accessStore.loadUser(context.getUsername());
                if (self == null) {
                    return GSON.toJson(List.of());
                }
                return GSON.toJson(List.of(self));
            }
        });

        get("/api/access/users/:username/access", (request, response) -> {
            response.type("application/json");
            String username = sanitizeUsername(request.params("username"));
            if (username == null || username.isBlank()) {
                return error(response, 400, "username manquant");
            }
            AccessContext context = resolveAccessContext(request, response);
            if (context == null) {
                return error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isActorPlatformAdmin() && !context.getUsername().equals(username)) {
                return error(response, 403, "Accès refusé");
            }
            try (AccessControlStore accessStore = new AccessControlStore()) {
                Map<String, Object> payload = accessStore.loadUserAccess(username);
                if (payload == null) {
                    return error(response, 404, "Utilisateur introuvable");
                }
                return GSON.toJson(payload);
            }
        });

        post("/api/access/users", (request, response) -> {
            response.type("application/json");
            AccessContext context = resolveAccessContext(request, response);
            if (context == null) {
                return error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isActorPlatformAdmin()) {
                return error(response, 403, "Droits insuffisants");
            }

            Map<String, Object> payload = readJsonBody(request.body());
            if (payload == null) {
                return error(response, 400, "Corps JSON manquant");
            }

            String username = sanitizeUsername(getString(payload.get("username")));
            String displayName = getString(payload.get("displayName"));
            if (username == null || username.isBlank()) {
                return error(response, 400, "username manquant");
            }

            boolean portalUser = getBoolean(payload.get("portalUser"));
            boolean portalModelAdmin = getBoolean(payload.get("portalModelAdmin"));
            boolean platformAdmin = getBoolean(payload.get("platformAdmin"));
            String password = getString(payload.get("password"));

            try (AccessControlStore accessStore = new AccessControlStore()) {
                accessStore.upsertUser(username, displayName, portalUser, portalModelAdmin, platformAdmin, password);
                Map<String, Object> created = accessStore.loadUserAccess(username);
                return GSON.toJson(created);
            }
        });

        put("/api/access/users/:username", (request, response) -> {
            response.type("application/json");
            AccessContext context = resolveAccessContext(request, response);
            if (context == null) {
                return error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isActorPlatformAdmin()) {
                return error(response, 403, "Droits insuffisants");
            }

            String username = sanitizeUsername(request.params("username"));
            if (username == null || username.isBlank()) {
                return error(response, 400, "username manquant");
            }

            Map<String, Object> payload = readJsonBody(request.body());
            if (payload == null) {
                return error(response, 400, "Corps JSON manquant");
            }

            String displayName = getString(payload.get("displayName"));
            boolean portalUser = getBoolean(payload.get("portalUser"));
            boolean portalModelAdmin = getBoolean(payload.get("portalModelAdmin"));
            boolean platformAdmin = getBoolean(payload.get("platformAdmin"));
            String password = getString(payload.get("password"));

            try (AccessControlStore accessStore = new AccessControlStore()) {
                if (accessStore.loadUser(username) == null) {
                    return error(response, 404, "Utilisateur introuvable");
                }
                accessStore.upsertUser(username, displayName, portalUser, portalModelAdmin, platformAdmin, password);
                Map<String, Object> updated = accessStore.loadUserAccess(username);
                return GSON.toJson(updated);
            }
        });

        delete("/api/access/users/:username", (request, response) -> {
            response.type("application/json");
            AccessContext context = resolveAccessContext(request, response);
            if (context == null) {
                return error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isActorPlatformAdmin()) {
                return error(response, 403, "Droits insuffisants");
            }
            String username = sanitizeUsername(request.params("username"));
            if (username == null || username.isBlank()) {
                return error(response, 400, "username manquant");
            }

            try (AccessControlStore accessStore = new AccessControlStore()) {
                boolean deleted = accessStore.deleteUser(username);
                if (!deleted) {
                    return error(response, 400, "Suppression refusée ou utilisateur introuvable");
                }
                return GSON.toJson(Map.of("status", "deleted", "username", username));
            }
        });

        put("/api/access/users/:username/permissions", (request, response) -> {
            response.type("application/json");
            AccessContext context = resolveAccessContext(request, response);
            if (context == null) {
                return error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isActorPlatformAdmin()) {
                return error(response, 403, "Droits insuffisants");
            }

            String username = sanitizeUsername(request.params("username"));
            if (username == null || username.isBlank()) {
                return error(response, 400, "username manquant");
            }

            Map<String, Object> payload = readJsonBody(request.body());
            if (payload == null) {
                return error(response, 400, "Corps JSON manquant");
            }

            List<Map<String, Object>> permissions = readPermissionList(payload.get("permissions"));
            try (AccessControlStore accessStore = new AccessControlStore()) {
                if (accessStore.loadUser(username) == null) {
                    return error(response, 404, "Utilisateur introuvable");
                }
                accessStore.replaceModelPermissions(username, permissions);
                Map<String, Object> updated = accessStore.loadUserAccess(username);
                return GSON.toJson(updated);
            }
        });

        get("/api/models", (request, response) -> {
            response.type("application/json");
            AccessContext context = resolveAccessContext(request, response);
            if (context == null) {
                return error(response, 401, "Utilisateur inconnu");
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
        });

        get("/api/models/:key", (request, response) -> {
            response.type("application/json");
            AccessContext context = resolveAccessContext(request, response);
            if (context == null) {
                return error(response, 401, "Utilisateur inconnu");
            }
            String modelKey = request.params("key");
            if (modelKey == null || modelKey.isBlank()) {
                return error(response, 400, "modelKey manquant");
            }
            if (!context.canViewModel(modelKey)) {
                return error(response, 403, "Accès au modèle refusé");
            }

            try (Neo4jModelStore store = new Neo4jModelStore()) {
                DATAMODEL model = store.loadModelByKey(modelKey);
                if (model == null) {
                    return error(response, 404, "Modèle introuvable");
                }
                return GSON.toJson(buildModelDetails(modelKey, model));
            }
        });

        get("/api/models/:key/xml", (request, response) -> {
            AccessContext context = resolveAccessContext(request, response);
            if (context == null) {
                response.type("application/json");
                return error(response, 401, "Utilisateur inconnu");
            }
            String modelKey = request.params("key");
            if (modelKey == null || modelKey.isBlank()) {
                response.type("application/json");
                return error(response, 400, "modelKey manquant");
            }
            if (!context.isPortalModelAdmin()) {
                response.type("application/json");
                return error(response, 403, "Accès portail administration refusé");
            }
            if (!context.canViewModel(modelKey)) {
                response.type("application/json");
                return error(response, 403, "Accès au modèle refusé");
            }

            try (Neo4jModelStore store = new Neo4jModelStore()) {
                String xml = store.loadModelXmlByKey(modelKey);
                if (xml == null || xml.isBlank()) {
                    response.type("application/json");
                    return error(response, 404, "Modèle introuvable");
                }
                response.type("application/xml");
                return xml;
            }
        });

        put("/api/models/:key", (request, response) -> {
            response.type("application/json");
            AccessContext context = resolveAccessContext(request, response);
            if (context == null) {
                return error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isPortalModelAdmin()) {
                return error(response, 403, "Accès portail administration refusé");
            }
            String modelKey = request.params("key");
            if (modelKey == null || modelKey.isBlank()) {
                return error(response, 400, "modelKey manquant");
            }
            if (!context.canViewModel(modelKey)) {
                return error(response, 403, "Accès au modèle refusé");
            }
            String xml = request.body();
            if (xml == null || xml.isBlank()) {
                return error(response, 400, "XML du modèle manquant");
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
                    return GSON.toJson(payload);
                }
            } catch (Exception e) {
                return error(response, 500, "Erreur lors de la mise à jour du modèle: " + e.getMessage());
            }
        });

        post("/api/models", (request, response) -> {
            response.type("application/json");
            AccessContext context = resolveAccessContext(request, response);
            if (context == null) {
                return error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isPortalModelAdmin()) {
                return error(response, 403, "Accès portail administration refusé");
            }
            try {
                String xml = readMultipartText(request.raw(), "modelFile");
                if (xml == null || xml.isBlank()) {
                    return error(response, 400, "Fichier modèle manquant");
                }

                ModelManager modelManager = ModelManager.getInstance();
                DATAMODEL model = modelManager.loadModelFromXml(xml);
                try (Neo4jModelStore store = new Neo4jModelStore()) {
                    String modelKey = store.storeModel(model, xml);
                    return GSON.toJson(buildModelDetails(modelKey, model));
                }
            } catch (Exception e) {
                return error(response, 500, "Erreur lors du chargement du modèle: " + e.getMessage());
            }
        });

        delete("/api/models/:key", (request, response) -> {
            response.type("application/json");
            AccessContext context = resolveAccessContext(request, response);
            if (context == null) {
                return error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isPortalModelAdmin()) {
                return error(response, 403, "Accès portail administration refusé");
            }
            String modelKey = request.params("key");
            if (modelKey == null || modelKey.isBlank()) {
                return error(response, 400, "modelKey manquant");
            }
            if (!context.canViewModel(modelKey)) {
                return error(response, 403, "Accès au modèle refusé");
            }
            try (Neo4jModelStore store = new Neo4jModelStore()) {
                store.deleteModelAndDataByKey(modelKey);
            }
            return GSON.toJson(Map.of("status", "deleted", "modelKey", modelKey));
        });

        post("/api/data", (request, response) -> {
            response.type("application/json");
            try {
                AccessContext context = resolveAccessContext(request, response);
                if (context == null) {
                    return error(response, 401, "Utilisateur inconnu");
                }
                if (!context.isPortalUser()) {
                    return error(response, 403, "Accès portail métier refusé");
                }
                configureMultipart(request.raw());
                String modelKey = readMultipartField(request.raw(), "modelKey");
                if (modelKey == null || modelKey.isBlank()) {
                    modelKey = request.raw().getParameter("modelKey");
                }
                String validateOnlyValue = readMultipartField(request.raw(), "validateOnly");
                if (validateOnlyValue == null || validateOnlyValue.isBlank()) {
                    validateOnlyValue = request.raw().getParameter("validateOnly");
                }
                boolean validateOnly = Boolean.parseBoolean(validateOnlyValue);
                if (modelKey == null || modelKey.isBlank()) {
                    return error(response, 400, "modelKey manquant");
                }
                if (!context.canCreateData(modelKey)) {
                    return error(response, 403, "Droit CREATE refusé pour ce modèle");
                }

                String xml = readMultipartText(request.raw(), "dataFile");
                if (xml == null || xml.isBlank()) {
                    return error(response, 400, "Fichier de données manquant");
                }

                try (Neo4jModelStore store = new Neo4jModelStore()) {
                    String modelXml = store.loadModelXmlByKey(modelKey);
                    if (modelXml == null || modelXml.isBlank()) {
                        return error(response, 404, "Modèle introuvable pour la clé fournie");
                    }
                    ModelManager.getInstance().loadModelFromXml(modelXml);
                }

                DATAS data = loadDataFromXml(xml);
                ModelBasedImportAPI importAPI = new ModelBasedImportAPI();
                ValidationResult result = importAPI.importData(data, validateOnly, modelKey);

                Map<String, Object> payload = new HashMap<>();
                payload.put("modelKey", modelKey);
                payload.put("valid", result.isValid());
                payload.put("validateOnly", validateOnly);
                payload.put("imported", result.isValid() && !validateOnly);
                payload.put("errors", result.getErrors());
                payload.put("warnings", result.getWarnings());
                payload.put("objectCount", countObjects(data));
                payload.put("linkCount", countLinks(data));
                return GSON.toJson(payload);
            } catch (Exception e) {
                return error(response, 500, "Erreur lors du chargement des données: " + e.getMessage());
            }
        });

        get("/api/data", (request, response) -> {
            response.type("application/json");
            AccessContext context = resolveAccessContext(request, response);
            if (context == null) {
                return error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isPortalUser()) {
                return error(response, 403, "Accès portail métier refusé");
            }
            String modelKey = request.queryParams("modelKey");
            if (modelKey == null || modelKey.isBlank()) {
                return error(response, 400, "modelKey manquant");
            }
            if (!context.canReadData(modelKey)) {
                return error(response, 403, "Droit READ refusé pour ce modèle");
            }

            try (Neo4jDataStore store = new Neo4jDataStore()) {
                List<Map<String, Object>> objects = store.loadObjects(modelKey);
                List<Map<String, Object>> links = store.loadLinks(modelKey);

                Set<String> objectTypes = new java.util.LinkedHashSet<>();
                for (Map<String, Object> object : objects) {
                    Object typeValue = object.get("type");
                    if (typeValue != null) {
                        objectTypes.add(typeValue.toString());
                    }
                }

                Map<String, Object> payload = new HashMap<>();
                payload.put("modelKey", modelKey);
                payload.put("objectCount", objects.size());
                payload.put("linkCount", links.size());
                payload.put("objectTypes", new ArrayList<>(objectTypes));
                payload.put("objects", objects);
                payload.put("links", links);
                return GSON.toJson(payload);
            }
        });

        post("/api/objects", (request, response) -> {
            response.type("application/json");
            AccessContext context = resolveAccessContext(request, response);
            if (context == null) {
                return error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isPortalUser()) {
                return error(response, 403, "Accès portail métier refusé");
            }
            Map<String, Object> payload = readJsonBody(request.body());
            if (payload == null) {
                return error(response, 400, "Corps JSON manquant");
            }
            String modelKey = getString(payload.get("modelKey"));
            String type = getString(payload.get("type"));
            Integer externalId = getInt(payload.get("id"));

            if (modelKey == null || modelKey.isBlank()) {
                return error(response, 400, "modelKey manquant");
            }
            if (!context.canCreateData(modelKey)) {
                return error(response, 403, "Droit CREATE refusé pour ce modèle");
            }
            if (type == null || type.isBlank()) {
                return error(response, 400, "type manquant");
            }

            List<DataPackAttribute> attributes = readAttributes(payload.get("attributes"));

            try (Neo4jModelStore store = new Neo4jModelStore()) {
                String modelXml = store.loadModelXmlByKey(modelKey);
                if (modelXml == null || modelXml.isBlank()) {
                    return error(response, 404, "Modèle introuvable pour la clé fournie");
                }
                ModelManager.getInstance().loadModelFromXml(modelXml);
            } catch (Exception e) {
                return error(response, 500, "Erreur lors du chargement du modèle: " + e.getMessage());
            }

            DATAS data = new DATAS();
            OBJECTS objects = new OBJECTS();
            data.setOBJECTS(objects);
            OBJECT obj = new OBJECT();
            obj.setID(externalId != null ? externalId : 0);
            obj.setTYPE(type);
            obj.getATTRIBUTE().addAll(attributes);
            objects.getOBJECT().add(obj);

            DataValidator validator = new DataValidator();
            ValidationResult result = validator.validate(data);
            if (!result.isValid()) {
                response.status(400);
                Map<String, Object> errorPayload = new HashMap<>();
                errorPayload.put("valid", false);
                errorPayload.put("errors", result.getErrors());
                errorPayload.put("warnings", result.getWarnings());
                return GSON.toJson(errorPayload);
            }

            try (CypherConnector connector = new CypherConnector()) {
                connector.setModelKey(modelKey);
                DataPackObject dataObject = new DataPackObject();
                dataObject.setTYPE(type);
                if (externalId != null && externalId > 0) {
                    dataObject.setID(externalId);
                }
                dataObject.getATTRIBUTE().addAll(attributes);
                int id = connector.writeObject(dataObject);
                Map<String, Object> resultPayload = new HashMap<>();
                resultPayload.put("status", "created");
                resultPayload.put("id", id);
                resultPayload.put("type", type);
                resultPayload.put("warnings", result.getWarnings());
                return GSON.toJson(resultPayload);
            } catch (Exception e) {
                return error(response, 500, "Erreur lors de la création de l'objet: " + e.getMessage());
            }
        });

        put("/api/objects/:id", (request, response) -> {
            response.type("application/json");
            AccessContext context = resolveAccessContext(request, response);
            if (context == null) {
                return error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isPortalUser()) {
                return error(response, 403, "Accès portail métier refusé");
            }

            Long objectId = getLong(request.params("id"));
            if (objectId == null || objectId <= 0) {
                return error(response, 400, "id objet invalide");
            }

            Map<String, Object> payload = readJsonBody(request.body());
            if (payload == null) {
                return error(response, 400, "Corps JSON manquant");
            }

            String modelKey = getString(payload.get("modelKey"));
            if (modelKey == null || modelKey.isBlank()) {
                return error(response, 400, "modelKey manquant");
            }
            if (!context.canUpdateData(modelKey)) {
                return error(response, 403, "Droit UPDATE refusé pour ce modèle");
            }

            List<DataPackAttribute> incomingAttributes = readAttributes(payload.get("attributes"));
            Map<String, Object> incomingAttributeMap = attributesToMap(incomingAttributes);

            try (Neo4jDataStore dataStore = new Neo4jDataStore();
                Neo4jModelStore modelStore = new Neo4jModelStore()) {
                Map<String, Object> existingObject = dataStore.loadObjectById(modelKey, objectId);
                if (existingObject == null) {
                    return error(response, 404, "Objet introuvable");
                }

                String existingType = getString(existingObject.get("type"));
                String requestedType = getString(payload.get("type"));
                if (requestedType != null && !requestedType.isBlank() && !requestedType.equals(existingType)) {
                    return error(response, 400, "Le type de l'objet ne peut pas être modifié");
                }

                Map<String, Object> mergedAttributes = new HashMap<>();
                if (existingObject.get("attributes") instanceof List<?> currentAttributes) {
                    for (Object currentAttribute : currentAttributes) {
                        if (!(currentAttribute instanceof Map<?, ?> attrMap)) {
                            continue;
                        }
                        Object keyValue = attrMap.get("key");
                        if (keyValue == null) {
                            continue;
                        }
                        mergedAttributes.put(keyValue.toString(), getString(attrMap.get("value")));
                    }
                }
                mergedAttributes.putAll(incomingAttributeMap);

                String modelXml = modelStore.loadModelXmlByKey(modelKey);
                if (modelXml == null || modelXml.isBlank()) {
                    return error(response, 404, "Modèle introuvable pour la clé fournie");
                }
                ModelManager.getInstance().loadModelFromXml(modelXml);

                DATAS data = new DATAS();
                OBJECTS objects = new OBJECTS();
                data.setOBJECTS(objects);
                OBJECT obj = new OBJECT();
                obj.setID(objectId.intValue());
                obj.setTYPE(existingType);
                obj.getATTRIBUTE().addAll(readAttributesFromMap(mergedAttributes));
                objects.getOBJECT().add(obj);

                DataValidator validator = new DataValidator();
                ValidationResult validationResult = validator.validate(data);
                if (!validationResult.isValid()) {
                    response.status(400);
                    Map<String, Object> errorPayload = new HashMap<>();
                    errorPayload.put("valid", false);
                    errorPayload.put("errors", validationResult.getErrors());
                    errorPayload.put("warnings", validationResult.getWarnings());
                    return GSON.toJson(errorPayload);
                }

                boolean updated = dataStore.updateObject(modelKey, objectId, mergedAttributes);
                if (!updated) {
                    return error(response, 404, "Objet introuvable");
                }

                Map<String, Object> resultPayload = new HashMap<>();
                resultPayload.put("status", "updated");
                resultPayload.put("id", objectId);
                resultPayload.put("type", existingType);
                resultPayload.put("warnings", validationResult.getWarnings());
                return GSON.toJson(resultPayload);
            } catch (Exception e) {
                return error(response, 500, "Erreur lors de la mise à jour de l'objet: " + e.getMessage());
            }
        });

        delete("/api/objects/:id", (request, response) -> {
            response.type("application/json");
            AccessContext context = resolveAccessContext(request, response);
            if (context == null) {
                return error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isPortalUser()) {
                return error(response, 403, "Accès portail métier refusé");
            }

            Long objectId = getLong(request.params("id"));
            if (objectId == null || objectId <= 0) {
                return error(response, 400, "id objet invalide");
            }

            String modelKey = request.queryParams("modelKey");
            if (modelKey == null || modelKey.isBlank()) {
                Map<String, Object> payload = readJsonBody(request.body());
                modelKey = payload == null ? null : getString(payload.get("modelKey"));
            }
            if (modelKey == null || modelKey.isBlank()) {
                return error(response, 400, "modelKey manquant");
            }
            if (!context.canDeleteData(modelKey)) {
                return error(response, 403, "Droit DELETE refusé pour ce modèle");
            }

            try (Neo4jDataStore dataStore = new Neo4jDataStore()) {
                boolean deleted = dataStore.deleteObject(modelKey, objectId);
                if (!deleted) {
                    return error(response, 404, "Objet introuvable");
                }
                return GSON.toJson(Map.of("status", "deleted", "id", objectId, "modelKey", modelKey));
            } catch (Exception e) {
                return error(response, 500, "Erreur lors de la suppression de l'objet: " + e.getMessage());
            }
        });

        post("/api/links", (request, response) -> {
            response.type("application/json");
            AccessContext context = resolveAccessContext(request, response);
            if (context == null) {
                return error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isPortalUser()) {
                return error(response, 403, "Accès portail métier refusé");
            }
            Map<String, Object> payload = readJsonBody(request.body());
            if (payload == null) {
                return error(response, 400, "Corps JSON manquant");
            }
            String modelKey = getString(payload.get("modelKey"));
            String linkTypeName = getString(payload.get("type"));
            Integer fromId = getInt(payload.get("fromId"));
            Integer toId = getInt(payload.get("toId"));

            if (modelKey == null || modelKey.isBlank()) {
                return error(response, 400, "modelKey manquant");
            }
            if (!context.canCreateData(modelKey)) {
                return error(response, 403, "Droit CREATE refusé pour ce modèle");
            }
            if (linkTypeName == null || linkTypeName.isBlank()) {
                return error(response, 400, "type manquant");
            }
            if (fromId == null || fromId <= 0 || toId == null || toId <= 0) {
                return error(response, 400, "fromId/toId invalides");
            }

            LINKTYPE linkType;
            try (Neo4jModelStore store = new Neo4jModelStore()) {
                String modelXml = store.loadModelXmlByKey(modelKey);
                if (modelXml == null || modelXml.isBlank()) {
                    return error(response, 404, "Modèle introuvable pour la clé fournie");
                }
                ModelManager.getInstance().loadModelFromXml(modelXml);
            } catch (Exception e) {
                return error(response, 500, "Erreur lors du chargement du modèle: " + e.getMessage());
            }

            ModelManager modelManager = ModelManager.getInstance();
            linkType = modelManager.getLinkType(linkTypeName);
            if (linkType == null) {
                return error(response, 400, "Type de lien inconnu: " + linkTypeName);
            }

            Map<String, Object> source;
            Map<String, Object> target;
            try (Neo4jDataStore store = new Neo4jDataStore()) {
                source = store.loadObjectById(modelKey, fromId);
                target = store.loadObjectById(modelKey, toId);
            } catch (Exception e) {
                return error(response, 500, "Erreur lors de la recherche des objets: " + e.getMessage());
            }

            if (source == null || target == null) {
                return error(response, 404, "Objet source ou cible introuvable");
            }

            String sourceType = source.get("type") == null ? "" : source.get("type").toString();
            String targetType = target.get("type") == null ? "" : target.get("type").toString();

            if (!isLinkTypeAllowed(modelManager, linkType, sourceType, true)) {
                return error(response, 400, "Type source non autorisé pour ce lien");
            }
            if (!isLinkTypeAllowed(modelManager, linkType, targetType, false)) {
                return error(response, 400, "Type cible non autorisé pour ce lien");
            }

            try (CypherConnector connector = new CypherConnector()) {
                connector.setModelKey(modelKey);
                DataPackObject objA = new DataPackObject();
                objA.setID(fromId);
                objA.setInternalId(fromId);
                objA.setTYPE(sourceType);

                DataPackObject objB = new DataPackObject();
                objB.setID(toId);
                objB.setInternalId(toId);
                objB.setTYPE(targetType);

                boolean directed = true;
                try {
                    directed = linkType.isDIRECTED();
                } catch (Exception ignored) {
                    directed = true;
                }
                connector.writeLink(objA, objB, directed, linkTypeName);

                Map<String, Object> resultPayload = new HashMap<>();
                resultPayload.put("status", "created");
                resultPayload.put("type", linkTypeName);
                resultPayload.put("fromId", fromId);
                resultPayload.put("toId", toId);
                return GSON.toJson(resultPayload);
            } catch (Exception e) {
                return error(response, 500, "Erreur lors de la création du lien: " + e.getMessage());
            }
        });
    }

    private static void addCorsHeaders(spark.Response response) {
        response.raw().setHeader("Access-Control-Allow-Origin", "*");
        response.raw().setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        response.raw().setHeader(
            "Access-Control-Allow-Headers",
            "Content-Type,Authorization,Accept,Origin,X-Session-Token"
        );
    }

    private static String readMultipartText(javax.servlet.http.HttpServletRequest request, String partName)
        throws Exception {
        configureMultipart(request);
        Part part = request.getPart(partName);
        if (part == null) {
            return null;
        }
        try (InputStream input = part.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            part.delete();
        }
    }

    private static String readMultipartField(javax.servlet.http.HttpServletRequest request, String partName)
        throws Exception {
        configureMultipart(request);
        Part part = request.getPart(partName);
        if (part == null) {
            return null;
        }
        try (InputStream input = part.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        } finally {
            part.delete();
        }
    }

    private static void configureMultipart(javax.servlet.http.HttpServletRequest request) {
        MultipartConfigElement config = new MultipartConfigElement(System.getProperty("java.io.tmpdir"));
        request.setAttribute("org.eclipse.jetty.multipartConfig", config);
    }

    private static DATAS loadDataFromXml(String xml) throws Exception {
        var context = jakarta.xml.bind.JAXBContext.newInstance(DATAS.class);
        var unmarshaller = context.createUnmarshaller();
        return (DATAS) unmarshaller.unmarshal(new java.io.StringReader(xml));
    }

    private static int countObjects(DATAS data) {
        if (data.getOBJECTS() == null || data.getOBJECTS().getOBJECT() == null) {
            return 0;
        }
        return data.getOBJECTS().getOBJECT().size();
    }

    private static int countLinks(DATAS data) {
        if (data.getLINKS() == null || data.getLINKS().getLINK() == null) {
            return 0;
        }
        return data.getLINKS().getLINK().size();
    }

    private static String error(spark.Response response, int status, String message) {
        response.status(status);
        return GSON.toJson(Map.of("error", message));
    }

    private static Map<String, Object> readJsonBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = GSON.fromJson(body, Map.class);
            return payload;
        } catch (Exception e) {
            return null;
        }
    }

    private static String getString(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private static Integer getInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long getLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean getBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private static List<DataPackAttribute> readAttributes(Object value) {
        List<DataPackAttribute> attributes = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return attributes;
        }
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            Object keyValue = map.get("key");
            if (keyValue == null) {
                continue;
            }
            String key = keyValue.toString();
            String val = map.get("value") == null ? "" : map.get("value").toString();
            attributes.add(new DataPackAttribute(key, val));
        }
        return attributes;
    }

    private static List<DataPackAttribute> readAttributesFromMap(Map<String, Object> attributes) {
        List<DataPackAttribute> rows = new ArrayList<>();
        if (attributes == null) {
            return rows;
        }
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            rows.add(new DataPackAttribute(key, entry.getValue() == null ? "" : entry.getValue().toString()));
        }
        return rows;
    }

    private static Map<String, Object> attributesToMap(List<DataPackAttribute> attributes) {
        Map<String, Object> map = new HashMap<>();
        if (attributes == null) {
            return map;
        }
        for (DataPackAttribute attribute : attributes) {
            if (attribute == null || attribute.getKEY() == null || attribute.getKEY().isBlank()) {
                continue;
            }
            map.put(attribute.getKEY(), attribute.getVALUE() == null ? "" : attribute.getVALUE());
        }
        return map;
    }

    private static List<Map<String, Object>> readPermissionList(Object value) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return rows;
        }
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            String modelKey = map.get("modelKey") == null ? null : map.get("modelKey").toString();
            if (modelKey == null || modelKey.isBlank()) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("modelKey", modelKey);
            row.put("visible", getBoolean(map.get("visible")));
            row.put("canRead", getBoolean(map.get("canRead")));
            row.put("canCreate", getBoolean(map.get("canCreate")));
            row.put("canUpdate", getBoolean(map.get("canUpdate")));
            row.put("canDelete", getBoolean(map.get("canDelete")));
            rows.add(row);
        }
        return rows;
    }

    private static String sanitizeUsername(String username) {
        if (username == null) {
            return null;
        }
        String value = username.trim();
        if (value.isBlank()) {
            return null;
        }
        if (!value.matches("[A-Za-z0-9._-]+")) {
            return null;
        }
        return value;
    }

    private static AccessContext resolveAccessContext(spark.Request request, spark.Response response) {
        String token = resolveSessionToken(request);
        if (token == null || token.isBlank()) {
            return null;
        }
        try (AccessControlStore accessStore = new AccessControlStore()) {
            Map<String, Object> session = accessStore.loadSession(token);
            return buildAccessContext(accessStore, session);
        } catch (Exception e) {
            response.status(500);
            return null;
        }
    }

    private static AccessContext buildAccessContext(AccessControlStore accessStore, Map<String, Object> session) {
        if (accessStore == null || session == null) {
            return null;
        }
        String token = getString(session.get("token"));
        String actorUsername = sanitizeUsername(getString(session.get("actorUsername")));
        String effectiveUsername = sanitizeUsername(getString(session.get("effectiveUsername")));
        if (token == null || token.isBlank() || actorUsername == null || effectiveUsername == null) {
            return null;
        }

        Map<String, Object> actorUser = accessStore.loadUser(actorUsername);
        Map<String, Object> effectiveUser = accessStore.loadUser(effectiveUsername);
        if (actorUser == null || effectiveUser == null) {
            return null;
        }

        List<Map<String, Object>> permissions = accessStore.listModelPermissions(effectiveUsername);
        boolean impersonating = getBoolean(session.get("impersonating"));
        long expiresAt = getLong(session.get("expiresAt")) == null ? 0L : getLong(session.get("expiresAt"));

        return new AccessContext(token, actorUser, effectiveUser, permissions, impersonating, expiresAt);
    }

    private static String resolveSessionToken(spark.Request request) {
        String authHeader = request.headers("Authorization");
        String token = AccessContext.extractBearerToken(authHeader);
        if (token != null && !token.isBlank()) {
            return token;
        }
        String fallback = request.headers("X-Session-Token");
        if (fallback == null || fallback.isBlank()) {
            return null;
        }
        return fallback.trim();
    }

    private static Map<String, Object> buildAuthPayload(AccessContext context, AccessControlStore accessStore) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", context.getSessionToken());
        payload.put("auth", buildAuthMeta(context));
        payload.put("user", accessStore.loadUser(context.getUsername()));
        payload.put("permissions", accessStore.listModelPermissions(context.getUsername()));
        return payload;
    }

    private static Map<String, Object> buildAuthMeta(AccessContext context) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("actorUsername", context.getActorUsername());
        meta.put("actorDisplayName", context.getActorDisplayName());
        meta.put("effectiveUsername", context.getUsername());
        meta.put("effectiveDisplayName", context.getDisplayName());
        meta.put("impersonating", context.isImpersonating());
        meta.put("actorPlatformAdmin", context.isActorPlatformAdmin());
        meta.put("expiresAt", context.getExpiresAt());
        return meta;
    }

    private static boolean isLinkTypeAllowed(ModelManager modelManager, LINKTYPE linkType, String candidateType, boolean source) {
        if (candidateType == null || candidateType.isBlank() || linkType == null) {
            return false;
        }
        List<TYPEREF> refs = null;
        if (source) {
            if (linkType.getSOURCETYPES() != null) {
                refs = linkType.getSOURCETYPES().getTYPEREF();
            }
        } else {
            if (linkType.getTARGETTYPES() != null) {
                refs = linkType.getTARGETTYPES().getTYPEREF();
            }
        }
        if (refs == null || refs.isEmpty()) {
            return false;
        }
        for (TYPEREF ref : refs) {
            if (ref == null || ref.getNAME() == null) {
                continue;
            }
            if (modelManager.isTypeOrSubtype(candidateType, ref.getNAME())) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> buildModelDetails(String modelKey, DATAMODEL model) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", modelKey);
        payload.put("name", model.getNAME());
        payload.put("version", model.getVERSION() == null ? "" : model.getVERSION());
        payload.put("defaultLanguage", model.getDEFAULTLANGUAGE() == null ? "" : model.getDEFAULTLANGUAGE());

        List<Map<String, Object>> languages = new ArrayList<>();
        if (model.getLANGUAGES() != null && model.getLANGUAGES().getLANGUAGE() != null) {
            for (LANGUAGE language : model.getLANGUAGES().getLANGUAGE()) {
                Map<String, Object> row = new HashMap<>();
                row.put("code", language.getCODE() == null ? "" : language.getCODE());
                row.put("label", language.getLABEL() == null ? "" : language.getLABEL());
                languages.add(row);
            }
        }
        payload.put("languages", languages);

        List<Map<String, Object>> objectTypes = new ArrayList<>();
        if (model.getOBJECTTYPES() != null && model.getOBJECTTYPES().getOBJECTTYPE() != null) {
            int index = 0;
            for (OBJECTTYPE objectType : model.getOBJECTTYPES().getOBJECTTYPE()) {
                Map<String, Object> row = new HashMap<>();
                row.put("key", objectType.getNAME() + "-" + index++);
                row.put("name", objectType.getNAME());
                row.put("parent", objectType.getPARENT() == null ? "" : objectType.getPARENT());
                row.put("icon", objectType.getICON() == null ? "" : objectType.getICON());
                row.put("description", objectType.getDESCRIPTION() == null ? "" : objectType.getDESCRIPTION());

                List<Map<String, Object>> attributes = new ArrayList<>();
                if (objectType.getATTRIBUTEDEFINITIONS() != null
                    && objectType.getATTRIBUTEDEFINITIONS().getATTRIBUTEDEFINITION() != null) {
                    for (ATTRIBUTEDEFINITION attribute :
                        objectType.getATTRIBUTEDEFINITIONS().getATTRIBUTEDEFINITION()) {
                        Map<String, Object> attr = new HashMap<>();
                        attr.put("name", attribute.getNAME());
                        attr.put("type", attribute.getTYPE() == null ? "STRING" : attribute.getTYPE().value());
                        attr.put("required", attribute.isREQUIRED());
                        attr.put("defaultValue", attribute.getDEFAULTVALUE() == null ? "" : attribute.getDEFAULTVALUE());
                        attr.put("searchable", Boolean.TRUE.equals(attribute.isSEARCHABLE()));
                        Map<String, String> labels = new HashMap<>();
                        if (attribute.getLABELS() != null && attribute.getLABELS().getLABEL() != null) {
                            for (LABEL label : attribute.getLABELS().getLABEL()) {
                                if (label.getLANGUAGE() != null && label.getVALUE() != null) {
                                    labels.put(label.getLANGUAGE(), label.getVALUE());
                                }
                            }
                        }
                        attr.put("labels", labels);
                        attr.put("description",
                            attribute.getDESCRIPTION() == null ? "" : attribute.getDESCRIPTION());
                        attributes.add(attr);
                    }
                }
                row.put("attributes", attributes);

                List<Map<String, Object>> representativeAttributes = new ArrayList<>();
                if (objectType.getREPRESENTATIVEATTRIBUTES() != null
                    && objectType.getREPRESENTATIVEATTRIBUTES().getATTRIBUTEREF() != null) {
                    int representativeIndex = 0;
                    for (ATTRIBUTEREF attributeRef : objectType.getREPRESENTATIVEATTRIBUTES().getATTRIBUTEREF()) {
                        Map<String, Object> refRow = new HashMap<>();
                        refRow.put("name", attributeRef.getNAME() == null ? "" : attributeRef.getNAME());
                        refRow.put("index", representativeIndex++);
                        if (attributeRef.getORDER() != null) {
                            refRow.put("order", attributeRef.getORDER());
                        }
                        representativeAttributes.add(refRow);
                    }
                }
                row.put("representativeAttributes", representativeAttributes);

                List<Map<String, Object>> attributeGroups = new ArrayList<>();
                if (objectType.getATTRIBUTEGROUPS() != null
                    && objectType.getATTRIBUTEGROUPS().getATTRIBUTEGROUP() != null) {
                    int groupIndex = 0;
                    for (ATTRIBUTEGROUP group : objectType.getATTRIBUTEGROUPS().getATTRIBUTEGROUP()) {
                        Map<String, Object> groupRow = new HashMap<>();
                        groupRow.put("key", (group.getNAME() == null ? "groupe" : group.getNAME()) + "-" + groupIndex++);
                        groupRow.put("name", group.getNAME() == null ? "" : group.getNAME());
                        if (group.getORDER() != null) {
                            groupRow.put("order", group.getORDER());
                        }

                        List<Map<String, Object>> groupAttributes = new ArrayList<>();
                        if (group.getATTRIBUTEREF() != null) {
                            int attributeIndex = 0;
                            for (ATTRIBUTEREF attributeRef : group.getATTRIBUTEREF()) {
                                Map<String, Object> refRow = new HashMap<>();
                                refRow.put("name", attributeRef.getNAME() == null ? "" : attributeRef.getNAME());
                                refRow.put("index", attributeIndex++);
                                if (attributeRef.getORDER() != null) {
                                    refRow.put("order", attributeRef.getORDER());
                                }
                                groupAttributes.add(refRow);
                            }
                        }
                        groupRow.put("attributes", groupAttributes);
                        attributeGroups.add(groupRow);
                    }
                }
                row.put("attributeGroups", attributeGroups);
                objectTypes.add(row);
            }
        }

        List<Map<String, Object>> linkTypes = new ArrayList<>();
        if (model.getLINKTYPES() != null && model.getLINKTYPES().getLINKTYPE() != null) {
            int index = 0;
            for (LINKTYPE linkType : model.getLINKTYPES().getLINKTYPE()) {
                Map<String, Object> row = new HashMap<>();
                row.put("key", linkType.getNAME() + "-" + index++);
                row.put("name", linkType.getNAME());
                row.put("directed", linkType.isDIRECTED());
                row.put("description", linkType.getDESCRIPTION() == null ? "" : linkType.getDESCRIPTION());

                List<String> sources = new ArrayList<>();
                if (linkType.getSOURCETYPES() != null && linkType.getSOURCETYPES().getTYPEREF() != null) {
                    for (TYPEREF typeRef : linkType.getSOURCETYPES().getTYPEREF()) {
                        if (typeRef.getNAME() != null) {
                            sources.add(typeRef.getNAME());
                        }
                    }
                }
                List<String> targets = new ArrayList<>();
                if (linkType.getTARGETTYPES() != null && linkType.getTARGETTYPES().getTYPEREF() != null) {
                    for (TYPEREF typeRef : linkType.getTARGETTYPES().getTYPEREF()) {
                        if (typeRef.getNAME() != null) {
                            targets.add(typeRef.getNAME());
                        }
                    }
                }

                List<Map<String, Object>> attributes = new ArrayList<>();
                if (linkType.getATTRIBUTEDEFINITIONS() != null
                    && linkType.getATTRIBUTEDEFINITIONS().getATTRIBUTEDEFINITION() != null) {
                    for (ATTRIBUTEDEFINITION attribute :
                        linkType.getATTRIBUTEDEFINITIONS().getATTRIBUTEDEFINITION()) {
                        Map<String, Object> attr = new HashMap<>();
                        attr.put("name", attribute.getNAME());
                        attr.put("type", attribute.getTYPE() == null ? "STRING" : attribute.getTYPE().value());
                        attr.put("required", attribute.isREQUIRED());
                        attr.put("defaultValue", attribute.getDEFAULTVALUE() == null ? "" : attribute.getDEFAULTVALUE());
                        attr.put("searchable", Boolean.TRUE.equals(attribute.isSEARCHABLE()));
                        Map<String, String> labels = new HashMap<>();
                        if (attribute.getLABELS() != null && attribute.getLABELS().getLABEL() != null) {
                            for (LABEL label : attribute.getLABELS().getLABEL()) {
                                if (label.getLANGUAGE() != null && label.getVALUE() != null) {
                                    labels.put(label.getLANGUAGE(), label.getVALUE());
                                }
                            }
                        }
                        attr.put("labels", labels);
                        attr.put("description",
                            attribute.getDESCRIPTION() == null ? "" : attribute.getDESCRIPTION());
                        attributes.add(attr);
                    }
                }

                row.put("sources", sources);
                row.put("targets", targets);
                row.put("attributes", attributes);
                linkTypes.add(row);
            }
        }

        payload.put("objectTypeCount", objectTypes.size());
        payload.put("linkTypeCount", linkTypes.size());
        payload.put("objectTypes", objectTypes);
        payload.put("linkTypes", linkTypes);

        return payload;
    }
}
