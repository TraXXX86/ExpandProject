package fr.expand.project.importdata.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.neo4j.driver.AuthToken;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;

import fr.expand.project.importdata.model.generated.ATTRIBUTEDEFINITION;
import fr.expand.project.importdata.model.generated.DATAMODEL;
import fr.expand.project.importdata.model.generated.LINKTYPE;
import fr.expand.project.importdata.model.generated.OBJECTTYPE;
import fr.expand.project.importdata.model.generated.TYPEREF;

/**
 * Persist a data model into Neo4j as a separate subgraph.
 */
public class Neo4jModelStore implements AutoCloseable {

    private static final String DEFAULT_BOLT_URI = "bolt://localhost:7687";
    private static final String DEFAULT_USER = "neo4j";
    private static final String DEFAULT_PASSWORD = "expand";

    private final Driver driver;

    public Neo4jModelStore() {
        String uri = readSetting("NEO4J_BOLT_URI", "NEO4J_URI", DEFAULT_BOLT_URI);
        AuthToken authToken = buildAuthToken();
        this.driver = GraphDatabase.driver(uri, authToken);
    }

    public String storeModel(DATAMODEL model) {
        if (model == null) {
            return null;
        }

        String modelName = model.getNAME();
        String modelVersion = model.getVERSION() == null ? "" : model.getVERSION();
        String modelKey = modelName + ":" + modelVersion;

        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                Map<String, Object> base = new HashMap<>();
                base.put("modelKey", modelKey);
                base.put("modelName", modelName);
                base.put("modelVersion", modelVersion);

                tx.run(
                    "MERGE (m:DataModel {key:$modelKey}) "
                        + "SET m.name=$modelName, m.version=$modelVersion, m.updatedAt=datetime()",
                    base
                );

                tx.run(
                    "MATCH (m:DataModel {key:$modelKey})-[:HAS_OBJECT_TYPE|HAS_LINK_TYPE]->(n) DETACH DELETE n",
                    base
                );

                Set<String> objectTypeNames = new HashSet<>();
                if (model.getOBJECTTYPES() != null && model.getOBJECTTYPES().getOBJECTTYPE() != null) {
                    for (OBJECTTYPE objectType : model.getOBJECTTYPES().getOBJECTTYPE()) {
                        if (objectType.getNAME() == null) {
                            continue;
                        }
                        objectTypeNames.add(objectType.getNAME());
                        Map<String, Object> params = new HashMap<>(base);
                        params.put("typeName", objectType.getNAME());
                        params.put("description", objectType.getDESCRIPTION() == null ? "" : objectType.getDESCRIPTION());

                        tx.run(
                            "MERGE (o:ModelObjectType {modelKey:$modelKey, name:$typeName}) "
                                + "SET o.description=$description",
                            params
                        );

                        tx.run(
                            "MATCH (m:DataModel {key:$modelKey}) "
                                + "MATCH (o:ModelObjectType {modelKey:$modelKey, name:$typeName}) "
                                + "MERGE (m)-[:HAS_OBJECT_TYPE]->(o)",
                            params
                        );

                        if (objectType.getATTRIBUTEDEFINITIONS() != null
                            && objectType.getATTRIBUTEDEFINITIONS().getATTRIBUTEDEFINITION() != null) {
                            for (ATTRIBUTEDEFINITION attribute :
                                objectType.getATTRIBUTEDEFINITIONS().getATTRIBUTEDEFINITION()) {
                                if (attribute.getNAME() == null) {
                                    continue;
                                }
                                Map<String, Object> attrParams = new HashMap<>(params);
                                attrParams.put("attributeKey",
                                    modelKey + "|OBJECT|" + objectType.getNAME() + "|" + attribute.getNAME());
                                attrParams.put("attributeName", attribute.getNAME());
                                attrParams.put("attributeType",
                                    attribute.getTYPE() == null ? "STRING" : attribute.getTYPE().value());
                                attrParams.put("attributeRequired", attribute.isREQUIRED());
                                attrParams.put("attributeDefault",
                                    attribute.getDEFAULTVALUE() == null ? "" : attribute.getDEFAULTVALUE());
                                attrParams.put("attributeDescription",
                                    attribute.getDESCRIPTION() == null ? "" : attribute.getDESCRIPTION());

                                tx.run(
                                    "CREATE (a:ModelAttribute {"
                                        + "key:$attributeKey, modelKey:$modelKey, scope:'OBJECT', "
                                        + "owner:$typeName, name:$attributeName, type:$attributeType, "
                                        + "required:$attributeRequired, defaultValue:$attributeDefault, "
                                        + "description:$attributeDescription"
                                        + "})",
                                    attrParams
                                );

                                tx.run(
                                    "MATCH (o:ModelObjectType {modelKey:$modelKey, name:$typeName}) "
                                        + "MATCH (a:ModelAttribute {key:$attributeKey}) "
                                        + "MERGE (o)-[:HAS_ATTRIBUTE]->(a)",
                                    attrParams
                                );
                            }
                        }
                    }
                }

                if (model.getOBJECTTYPES() != null && model.getOBJECTTYPES().getOBJECTTYPE() != null) {
                    for (OBJECTTYPE objectType : model.getOBJECTTYPES().getOBJECTTYPE()) {
                        if (objectType.getNAME() == null || objectType.getPARENT() == null) {
                            continue;
                        }
                        if (!objectTypeNames.contains(objectType.getPARENT())) {
                            continue;
                        }
                        Map<String, Object> params = new HashMap<>(base);
                        params.put("childName", objectType.getNAME());
                        params.put("parentName", objectType.getPARENT());
                        tx.run(
                            "MATCH (child:ModelObjectType {modelKey:$modelKey, name:$childName}) "
                                + "MATCH (parent:ModelObjectType {modelKey:$modelKey, name:$parentName}) "
                                + "MERGE (child)-[:EXTENDS]->(parent)",
                            params
                        );
                    }
                }

                if (model.getLINKTYPES() != null && model.getLINKTYPES().getLINKTYPE() != null) {
                    for (LINKTYPE linkType : model.getLINKTYPES().getLINKTYPE()) {
                        if (linkType.getNAME() == null) {
                            continue;
                        }
                        Map<String, Object> params = new HashMap<>(base);
                        params.put("linkName", linkType.getNAME());
                        params.put("description", linkType.getDESCRIPTION() == null ? "" : linkType.getDESCRIPTION());
                        params.put("directed", linkType.isDIRECTED());

                        tx.run(
                            "MERGE (l:ModelLinkType {modelKey:$modelKey, name:$linkName}) "
                                + "SET l.description=$description, l.directed=$directed",
                            params
                        );

                        tx.run(
                            "MATCH (m:DataModel {key:$modelKey}) "
                                + "MATCH (l:ModelLinkType {modelKey:$modelKey, name:$linkName}) "
                                + "MERGE (m)-[:HAS_LINK_TYPE]->(l)",
                            params
                        );

                        if (linkType.getSOURCETYPES() != null && linkType.getSOURCETYPES().getTYPEREF() != null) {
                            for (TYPEREF typeRef : linkType.getSOURCETYPES().getTYPEREF()) {
                                if (typeRef.getNAME() == null || !objectTypeNames.contains(typeRef.getNAME())) {
                                    continue;
                                }
                                Map<String, Object> relParams = new HashMap<>(params);
                                relParams.put("objectType", typeRef.getNAME());
                                tx.run(
                                    "MATCH (l:ModelLinkType {modelKey:$modelKey, name:$linkName}) "
                                        + "MATCH (o:ModelObjectType {modelKey:$modelKey, name:$objectType}) "
                                        + "MERGE (l)-[:ALLOWS_SOURCE]->(o)",
                                    relParams
                                );
                            }
                        }

                        if (linkType.getTARGETTYPES() != null && linkType.getTARGETTYPES().getTYPEREF() != null) {
                            for (TYPEREF typeRef : linkType.getTARGETTYPES().getTYPEREF()) {
                                if (typeRef.getNAME() == null || !objectTypeNames.contains(typeRef.getNAME())) {
                                    continue;
                                }
                                Map<String, Object> relParams = new HashMap<>(params);
                                relParams.put("objectType", typeRef.getNAME());
                                tx.run(
                                    "MATCH (l:ModelLinkType {modelKey:$modelKey, name:$linkName}) "
                                        + "MATCH (o:ModelObjectType {modelKey:$modelKey, name:$objectType}) "
                                        + "MERGE (l)-[:ALLOWS_TARGET]->(o)",
                                    relParams
                                );
                            }
                        }

                        if (linkType.getATTRIBUTEDEFINITIONS() != null
                            && linkType.getATTRIBUTEDEFINITIONS().getATTRIBUTEDEFINITION() != null) {
                            for (ATTRIBUTEDEFINITION attribute :
                                linkType.getATTRIBUTEDEFINITIONS().getATTRIBUTEDEFINITION()) {
                                if (attribute.getNAME() == null) {
                                    continue;
                                }
                                Map<String, Object> attrParams = new HashMap<>(params);
                                attrParams.put("attributeKey",
                                    modelKey + "|LINK|" + linkType.getNAME() + "|" + attribute.getNAME());
                                attrParams.put("attributeName", attribute.getNAME());
                                attrParams.put("attributeType",
                                    attribute.getTYPE() == null ? "STRING" : attribute.getTYPE().value());
                                attrParams.put("attributeRequired", attribute.isREQUIRED());
                                attrParams.put("attributeDefault",
                                    attribute.getDEFAULTVALUE() == null ? "" : attribute.getDEFAULTVALUE());
                                attrParams.put("attributeDescription",
                                    attribute.getDESCRIPTION() == null ? "" : attribute.getDESCRIPTION());

                                tx.run(
                                    "CREATE (a:ModelAttribute {"
                                        + "key:$attributeKey, modelKey:$modelKey, scope:'LINK', "
                                        + "owner:$linkName, name:$attributeName, type:$attributeType, "
                                        + "required:$attributeRequired, defaultValue:$attributeDefault, "
                                        + "description:$attributeDescription"
                                        + "})",
                                    attrParams
                                );

                                tx.run(
                                    "MATCH (l:ModelLinkType {modelKey:$modelKey, name:$linkName}) "
                                        + "MATCH (a:ModelAttribute {key:$attributeKey}) "
                                        + "MERGE (l)-[:HAS_ATTRIBUTE]->(a)",
                                    attrParams
                                );
                            }
                        }
                    }
                }

                return null;
            });
        }
        return modelKey;
    }

    public void deleteModelAndData(String modelName, String modelVersion) {
        String version = modelVersion == null ? "" : modelVersion;
        String key = modelName + ":" + version;
        deleteModelAndDataByKey(key);
    }

    public void deleteModelAndDataByKey(String modelKey) {
        if (modelKey == null || modelKey.isBlank()) {
            return;
        }

        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                Map<String, Object> params = new HashMap<>();
                params.put("modelKey", modelKey);
                tx.run("MATCH (n {modelKey:$modelKey}) DETACH DELETE n", params);
                tx.run(
                    "MATCH (m:DataModel {key:$modelKey})-[:HAS_OBJECT_TYPE|HAS_LINK_TYPE]->(n) DETACH DELETE n",
                    params
                );
                tx.run("MATCH (m:DataModel {key:$modelKey}) DETACH DELETE m", params);
                return null;
            });
        }
    }

    @Override
    public void close() {
        if (driver != null) {
            driver.close();
        }
    }

    private AuthToken buildAuthToken() {
        String auth = readSetting("NEO4J_AUTH", null, null);
        if (auth != null && !auth.isBlank()) {
            if ("none".equalsIgnoreCase(auth.trim())) {
                return AuthTokens.none();
            }
            int separatorIndex = auth.indexOf('/');
            if (separatorIndex > 0 && separatorIndex < auth.length() - 1) {
                String user = auth.substring(0, separatorIndex);
                String password = auth.substring(separatorIndex + 1);
                return AuthTokens.basic(user, password);
            }
        }

        String user = readSetting("NEO4J_USER", null, DEFAULT_USER);
        String password = readSetting("NEO4J_PASSWORD", null, DEFAULT_PASSWORD);
        return AuthTokens.basic(user, password);
    }

    private String readSetting(String envKey, String fallbackEnvKey, String defaultValue) {
        String value = System.getProperty(envKey);
        if (value == null || value.isBlank()) {
            value = System.getenv(envKey);
        }
        if ((value == null || value.isBlank()) && fallbackEnvKey != null) {
            value = System.getProperty(fallbackEnvKey);
            if (value == null || value.isBlank()) {
                value = System.getenv(fallbackEnvKey);
            }
        }
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
