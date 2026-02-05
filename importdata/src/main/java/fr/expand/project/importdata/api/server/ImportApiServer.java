package fr.expand.project.importdata.api.server;

import static spark.Spark.before;
import static spark.Spark.delete;
import static spark.Spark.get;
import static spark.Spark.options;
import static spark.Spark.port;
import static spark.Spark.post;
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

import fr.expand.project.importdata.api.impl.ModelBasedImportAPI;
import fr.expand.project.importdata.data.Neo4jDataStore;
import fr.expand.project.importdata.dto.generated.DATAS;
import fr.expand.project.importdata.model.ModelManager;
import fr.expand.project.importdata.model.Neo4jModelStore;
import fr.expand.project.importdata.model.generated.ATTRIBUTEDEFINITION;
import fr.expand.project.importdata.model.generated.DATAMODEL;
import fr.expand.project.importdata.model.generated.LINKTYPE;
import fr.expand.project.importdata.model.generated.OBJECTTYPE;
import fr.expand.project.importdata.model.generated.TYPEREF;
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

        get("/api/models", (request, response) -> {
            response.type("application/json");
            try (Neo4jModelStore store = new Neo4jModelStore()) {
                return GSON.toJson(store.listModels());
            }
        });

        get("/api/models/:key", (request, response) -> {
            response.type("application/json");
            String modelKey = request.params("key");
            if (modelKey == null || modelKey.isBlank()) {
                return error(response, 400, "modelKey manquant");
            }

            try (Neo4jModelStore store = new Neo4jModelStore()) {
                DATAMODEL model = store.loadModelByKey(modelKey);
                if (model == null) {
                    return error(response, 404, "Modèle introuvable");
                }
                return GSON.toJson(buildModelDetails(modelKey, model));
            }
        });

        post("/api/models", (request, response) -> {
            response.type("application/json");
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
            String modelKey = request.params("key");
            if (modelKey == null || modelKey.isBlank()) {
                return error(response, 400, "modelKey manquant");
            }
            try (Neo4jModelStore store = new Neo4jModelStore()) {
                store.deleteModelAndDataByKey(modelKey);
            }
            return GSON.toJson(Map.of("status", "deleted", "modelKey", modelKey));
        });

        post("/api/data", (request, response) -> {
            response.type("application/json");
            try {
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
            String modelKey = request.queryParams("modelKey");
            if (modelKey == null || modelKey.isBlank()) {
                return error(response, 400, "modelKey manquant");
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
    }

    private static void addCorsHeaders(spark.Response response) {
        response.header("Access-Control-Allow-Origin", "*");
        response.header("Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS");
        response.header("Access-Control-Allow-Headers", "Content-Type,Authorization,Accept,Origin");
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

    private static Map<String, Object> buildModelDetails(String modelKey, DATAMODEL model) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", modelKey);
        payload.put("name", model.getNAME());
        payload.put("version", model.getVERSION() == null ? "" : model.getVERSION());

        List<Map<String, Object>> objectTypes = new ArrayList<>();
        if (model.getOBJECTTYPES() != null && model.getOBJECTTYPES().getOBJECTTYPE() != null) {
            int index = 0;
            for (OBJECTTYPE objectType : model.getOBJECTTYPES().getOBJECTTYPE()) {
                Map<String, Object> row = new HashMap<>();
                row.put("key", objectType.getNAME() + "-" + index++);
                row.put("name", objectType.getNAME());
                row.put("parent", objectType.getPARENT() == null ? "" : objectType.getPARENT());
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
                        attr.put("description",
                            attribute.getDESCRIPTION() == null ? "" : attribute.getDESCRIPTION());
                        attributes.add(attr);
                    }
                }
                row.put("attributes", attributes);
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
