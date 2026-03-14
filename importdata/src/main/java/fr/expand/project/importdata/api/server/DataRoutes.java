package fr.expand.project.importdata.api.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static spark.Spark.delete;
import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.put;

import fr.expand.project.importdata.access.AccessContext;
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
import fr.expand.project.importdata.model.generated.LINKTYPE;
import fr.expand.project.importdata.validation.DataValidator;
import fr.expand.project.importdata.validation.ValidationResult;
import spark.Response;

final class DataRoutes {

    private final ApiSupport apiSupport;
    private final AccessSupport accessSupport;
    private final ModelPayloadMapper modelPayloadMapper;

    DataRoutes(ApiSupport apiSupport, AccessSupport accessSupport, ModelPayloadMapper modelPayloadMapper) {
        this.apiSupport = apiSupport;
        this.accessSupport = accessSupport;
        this.modelPayloadMapper = modelPayloadMapper;
    }

    void register() {
        registerDataImportRoutes();
        registerObjectRoutes();
        registerLinkRoutes();
    }

    private void registerDataImportRoutes() {
        post("/api/data", (request, response) -> {
            response.type("application/json");
            try {
                AccessContext context = accessSupport.resolveAccessContext(request, response);
                if (context == null) {
                    return apiSupport.error(response, 401, "Utilisateur inconnu");
                }
                if (!context.isPortalUser()) {
                    return apiSupport.error(response, 403, "Accès portail métier refusé");
                }

                apiSupport.configureMultipart(request.raw());
                String modelKey = apiSupport.readMultipartField(request.raw(), "modelKey");
                if (modelKey == null || modelKey.isBlank()) {
                    modelKey = request.raw().getParameter("modelKey");
                }

                String validateOnlyValue = apiSupport.readMultipartField(request.raw(), "validateOnly");
                if (validateOnlyValue == null || validateOnlyValue.isBlank()) {
                    validateOnlyValue = request.raw().getParameter("validateOnly");
                }
                boolean validateOnly = Boolean.parseBoolean(validateOnlyValue);

                if (modelKey == null || modelKey.isBlank()) {
                    return apiSupport.error(response, 400, "modelKey manquant");
                }
                if (!context.canCreateData(modelKey)) {
                    return apiSupport.error(response, 403, "Droit CREATE refusé pour ce modèle");
                }

                String xml = apiSupport.readMultipartText(request.raw(), "dataFile");
                if (xml == null || xml.isBlank()) {
                    return apiSupport.error(response, 400, "Fichier de données manquant");
                }

                try (Neo4jModelStore store = new Neo4jModelStore()) {
                    String modelXml = store.loadModelXmlByKey(modelKey);
                    if (modelXml == null || modelXml.isBlank()) {
                        return apiSupport.error(response, 404, "Modèle introuvable pour la clé fournie");
                    }
                    ModelManager.getInstance().loadModelFromXml(modelXml);
                }

                DATAS data = apiSupport.loadDataFromXml(xml);
                ModelBasedImportAPI importAPI = new ModelBasedImportAPI();
                ValidationResult result = importAPI.importData(data, validateOnly, modelKey);

                Map<String, Object> payload = new HashMap<>();
                payload.put("modelKey", modelKey);
                payload.put("valid", result.isValid());
                payload.put("validateOnly", validateOnly);
                payload.put("imported", result.isValid() && !validateOnly);
                payload.put("errors", result.getErrors());
                payload.put("warnings", result.getWarnings());
                payload.put("objectCount", apiSupport.countObjects(data));
                payload.put("linkCount", apiSupport.countLinks(data));
                return apiSupport.toJson(payload);
            } catch (Exception e) {
                return apiSupport.error(response, 500, "Erreur lors du chargement des données: " + e.getMessage());
            }
        });

        get("/api/data", (request, response) -> {
            response.type("application/json");
            AccessContext context = accessSupport.resolveAccessContext(request, response);
            if (context == null) {
                return apiSupport.error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isPortalUser()) {
                return apiSupport.error(response, 403, "Accès portail métier refusé");
            }

            String modelKey = request.queryParams("modelKey");
            if (modelKey == null || modelKey.isBlank()) {
                return apiSupport.error(response, 400, "modelKey manquant");
            }
            if (!context.canReadData(modelKey)) {
                return apiSupport.error(response, 403, "Droit READ refusé pour ce modèle");
            }

            try (Neo4jDataStore store = new Neo4jDataStore()) {
                List<Map<String, Object>> objects = store.loadObjects(modelKey);
                List<Map<String, Object>> links = store.loadLinks(modelKey);

                Set<String> objectTypes = new LinkedHashSet<>();
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
                return apiSupport.toJson(payload);
            }
        });
    }

    private void registerObjectRoutes() {
        post("/api/objects", (request, response) -> {
            response.type("application/json");
            AccessContext context = accessSupport.resolveAccessContext(request, response);
            if (context == null) {
                return apiSupport.error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isPortalUser()) {
                return apiSupport.error(response, 403, "Accès portail métier refusé");
            }

            Map<String, Object> payload = apiSupport.readJsonBody(request.body());
            if (payload == null) {
                return apiSupport.error(response, 400, "Corps JSON manquant");
            }

            String modelKey = apiSupport.getString(payload.get("modelKey"));
            String type = apiSupport.getString(payload.get("type"));
            Integer externalId = apiSupport.getInt(payload.get("id"));

            if (modelKey == null || modelKey.isBlank()) {
                return apiSupport.error(response, 400, "modelKey manquant");
            }
            if (!context.canCreateData(modelKey)) {
                return apiSupport.error(response, 403, "Droit CREATE refusé pour ce modèle");
            }
            if (type == null || type.isBlank()) {
                return apiSupport.error(response, 400, "type manquant");
            }

            List<DataPackAttribute> attributes = apiSupport.readAttributes(payload.get("attributes"));

            try (Neo4jModelStore store = new Neo4jModelStore()) {
                String modelXml = store.loadModelXmlByKey(modelKey);
                if (modelXml == null || modelXml.isBlank()) {
                    return apiSupport.error(response, 404, "Modèle introuvable pour la clé fournie");
                }
                ModelManager.getInstance().loadModelFromXml(modelXml);
            } catch (Exception e) {
                return apiSupport.error(response, 500, "Erreur lors du chargement du modèle: " + e.getMessage());
            }

            DATAS data = new DATAS();
            OBJECTS objects = new OBJECTS();
            data.setOBJECTS(objects);
            OBJECT object = new OBJECT();
            object.setID(externalId != null ? externalId : 0);
            object.setTYPE(type);
            object.getATTRIBUTE().addAll(attributes);
            objects.getOBJECT().add(object);

            DataValidator validator = new DataValidator();
            ValidationResult result = validator.validate(data);
            if (!result.isValid()) {
                return buildValidationError(response, result);
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
                return apiSupport.toJson(resultPayload);
            } catch (Exception e) {
                return apiSupport.error(response, 500, "Erreur lors de la création de l'objet: " + e.getMessage());
            }
        });

        put("/api/objects/:id", (request, response) -> {
            response.type("application/json");
            AccessContext context = accessSupport.resolveAccessContext(request, response);
            if (context == null) {
                return apiSupport.error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isPortalUser()) {
                return apiSupport.error(response, 403, "Accès portail métier refusé");
            }

            Long objectId = apiSupport.getLong(request.params("id"));
            if (objectId == null || objectId <= 0) {
                return apiSupport.error(response, 400, "id objet invalide");
            }

            Map<String, Object> payload = apiSupport.readJsonBody(request.body());
            if (payload == null) {
                return apiSupport.error(response, 400, "Corps JSON manquant");
            }

            String modelKey = apiSupport.getString(payload.get("modelKey"));
            if (modelKey == null || modelKey.isBlank()) {
                return apiSupport.error(response, 400, "modelKey manquant");
            }
            if (!context.canUpdateData(modelKey)) {
                return apiSupport.error(response, 403, "Droit UPDATE refusé pour ce modèle");
            }

            List<DataPackAttribute> incomingAttributes = apiSupport.readAttributes(payload.get("attributes"));
            Map<String, Object> incomingAttributeMap = apiSupport.attributesToMap(incomingAttributes);

            try (Neo4jDataStore dataStore = new Neo4jDataStore();
                Neo4jModelStore modelStore = new Neo4jModelStore()) {
                Map<String, Object> existingObject = dataStore.loadObjectById(modelKey, objectId);
                if (existingObject == null) {
                    return apiSupport.error(response, 404, "Objet introuvable");
                }

                String existingType = apiSupport.getString(existingObject.get("type"));
                String requestedType = apiSupport.getString(payload.get("type"));
                if (requestedType != null && !requestedType.isBlank() && !requestedType.equals(existingType)) {
                    return apiSupport.error(response, 400, "Le type de l'objet ne peut pas être modifié");
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
                        mergedAttributes.put(keyValue.toString(), apiSupport.getString(attrMap.get("value")));
                    }
                }
                mergedAttributes.putAll(incomingAttributeMap);

                String modelXml = modelStore.loadModelXmlByKey(modelKey);
                if (modelXml == null || modelXml.isBlank()) {
                    return apiSupport.error(response, 404, "Modèle introuvable pour la clé fournie");
                }
                ModelManager.getInstance().loadModelFromXml(modelXml);

                DATAS data = new DATAS();
                OBJECTS objects = new OBJECTS();
                data.setOBJECTS(objects);
                OBJECT object = new OBJECT();
                object.setID(objectId.intValue());
                object.setTYPE(existingType);
                object.getATTRIBUTE().addAll(apiSupport.readAttributesFromMap(mergedAttributes));
                objects.getOBJECT().add(object);

                DataValidator validator = new DataValidator();
                ValidationResult validationResult = validator.validate(data);
                if (!validationResult.isValid()) {
                    return buildValidationError(response, validationResult);
                }

                boolean updated = dataStore.updateObject(modelKey, objectId, mergedAttributes);
                if (!updated) {
                    return apiSupport.error(response, 404, "Objet introuvable");
                }

                Map<String, Object> resultPayload = new HashMap<>();
                resultPayload.put("status", "updated");
                resultPayload.put("id", objectId);
                resultPayload.put("type", existingType);
                resultPayload.put("warnings", validationResult.getWarnings());
                return apiSupport.toJson(resultPayload);
            } catch (Exception e) {
                return apiSupport.error(response, 500, "Erreur lors de la mise à jour de l'objet: " + e.getMessage());
            }
        });

        delete("/api/objects/:id", (request, response) -> {
            response.type("application/json");
            AccessContext context = accessSupport.resolveAccessContext(request, response);
            if (context == null) {
                return apiSupport.error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isPortalUser()) {
                return apiSupport.error(response, 403, "Accès portail métier refusé");
            }

            Long objectId = apiSupport.getLong(request.params("id"));
            if (objectId == null || objectId <= 0) {
                return apiSupport.error(response, 400, "id objet invalide");
            }

            String modelKey = request.queryParams("modelKey");
            if (modelKey == null || modelKey.isBlank()) {
                Map<String, Object> payload = apiSupport.readJsonBody(request.body());
                modelKey = payload == null ? null : apiSupport.getString(payload.get("modelKey"));
            }
            if (modelKey == null || modelKey.isBlank()) {
                return apiSupport.error(response, 400, "modelKey manquant");
            }
            if (!context.canDeleteData(modelKey)) {
                return apiSupport.error(response, 403, "Droit DELETE refusé pour ce modèle");
            }

            try (Neo4jDataStore dataStore = new Neo4jDataStore()) {
                boolean deleted = dataStore.deleteObject(modelKey, objectId);
                if (!deleted) {
                    return apiSupport.error(response, 404, "Objet introuvable");
                }
                return apiSupport.toJson(Map.of("status", "deleted", "id", objectId, "modelKey", modelKey));
            } catch (Exception e) {
                return apiSupport.error(response, 500, "Erreur lors de la suppression de l'objet: " + e.getMessage());
            }
        });
    }

    private void registerLinkRoutes() {
        post("/api/links", (request, response) -> {
            response.type("application/json");
            AccessContext context = accessSupport.resolveAccessContext(request, response);
            if (context == null) {
                return apiSupport.error(response, 401, "Utilisateur inconnu");
            }
            if (!context.isPortalUser()) {
                return apiSupport.error(response, 403, "Accès portail métier refusé");
            }

            Map<String, Object> payload = apiSupport.readJsonBody(request.body());
            if (payload == null) {
                return apiSupport.error(response, 400, "Corps JSON manquant");
            }

            String modelKey = apiSupport.getString(payload.get("modelKey"));
            String linkTypeName = apiSupport.getString(payload.get("type"));
            Integer fromId = apiSupport.getInt(payload.get("fromId"));
            Integer toId = apiSupport.getInt(payload.get("toId"));

            if (modelKey == null || modelKey.isBlank()) {
                return apiSupport.error(response, 400, "modelKey manquant");
            }
            if (!context.canCreateData(modelKey)) {
                return apiSupport.error(response, 403, "Droit CREATE refusé pour ce modèle");
            }
            if (linkTypeName == null || linkTypeName.isBlank()) {
                return apiSupport.error(response, 400, "type manquant");
            }
            if (fromId == null || fromId <= 0 || toId == null || toId <= 0) {
                return apiSupport.error(response, 400, "fromId/toId invalides");
            }

            try (Neo4jModelStore store = new Neo4jModelStore()) {
                String modelXml = store.loadModelXmlByKey(modelKey);
                if (modelXml == null || modelXml.isBlank()) {
                    return apiSupport.error(response, 404, "Modèle introuvable pour la clé fournie");
                }
                ModelManager.getInstance().loadModelFromXml(modelXml);
            } catch (Exception e) {
                return apiSupport.error(response, 500, "Erreur lors du chargement du modèle: " + e.getMessage());
            }

            ModelManager modelManager = ModelManager.getInstance();
            LINKTYPE linkType = modelManager.getLinkType(linkTypeName);
            if (linkType == null) {
                return apiSupport.error(response, 400, "Type de lien inconnu: " + linkTypeName);
            }

            Map<String, Object> source;
            Map<String, Object> target;
            try (Neo4jDataStore store = new Neo4jDataStore()) {
                source = store.loadObjectById(modelKey, fromId);
                target = store.loadObjectById(modelKey, toId);
            } catch (Exception e) {
                return apiSupport.error(response, 500, "Erreur lors de la recherche des objets: " + e.getMessage());
            }

            if (source == null || target == null) {
                return apiSupport.error(response, 404, "Objet source ou cible introuvable");
            }

            String sourceType = source.get("type") == null ? "" : source.get("type").toString();
            String targetType = target.get("type") == null ? "" : target.get("type").toString();

            if (!modelPayloadMapper.isLinkTypeAllowed(modelManager, linkType, sourceType, true)) {
                return apiSupport.error(response, 400, "Type source non autorisé pour ce lien");
            }
            if (!modelPayloadMapper.isLinkTypeAllowed(modelManager, linkType, targetType, false)) {
                return apiSupport.error(response, 400, "Type cible non autorisé pour ce lien");
            }

            try (CypherConnector connector = new CypherConnector()) {
                connector.setModelKey(modelKey);
                DataPackObject objectA = new DataPackObject();
                objectA.setID(fromId);
                objectA.setInternalId(fromId);
                objectA.setTYPE(sourceType);

                DataPackObject objectB = new DataPackObject();
                objectB.setID(toId);
                objectB.setInternalId(toId);
                objectB.setTYPE(targetType);

                boolean directed = true;
                try {
                    directed = linkType.isDIRECTED();
                } catch (Exception ignored) {
                    directed = true;
                }
                connector.writeLink(objectA, objectB, directed, linkTypeName);

                Map<String, Object> resultPayload = new HashMap<>();
                resultPayload.put("status", "created");
                resultPayload.put("type", linkTypeName);
                resultPayload.put("fromId", fromId);
                resultPayload.put("toId", toId);
                return apiSupport.toJson(resultPayload);
            } catch (Exception e) {
                return apiSupport.error(response, 500, "Erreur lors de la création du lien: " + e.getMessage());
            }
        });
    }

    private String buildValidationError(Response response, ValidationResult validationResult) {
        response.status(400);
        Map<String, Object> payload = new HashMap<>();
        payload.put("valid", false);
        payload.put("errors", validationResult.getErrors());
        payload.put("warnings", validationResult.getWarnings());
        return apiSupport.toJson(payload);
    }
}
