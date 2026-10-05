package fr.expand.project.importdata.model;

import fr.expand.project.importdata.audit.*;
import fr.expand.project.importdata.model.generated.ATTRIBUTEDEFINITION;
import fr.expand.project.importdata.model.generated.DATAMODEL;
import fr.expand.project.importdata.model.generated.LINKTYPE;
import fr.expand.project.importdata.model.generated.OBJECTTYPE;
import fr.expand.project.importdata.model.generated.TYPEREF;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Persist a data model into Neo4j as a separate subgraph. */
public class Neo4jModelStore implements AutoCloseable {

    private final Driver driver;
    private final AuditActor actor;

    public Neo4jModelStore() {
        this(AuditActor.system());
    }

    public Neo4jModelStore(AuditActor actor) {
        this.actor = java.util.Objects.requireNonNull(actor);
        this.driver = fr.expand.project.importdata.dao.Neo4jDriverProvider.getDriver();
    }

    public static String keyFor(DATAMODEL model) {
        if (model == null || model.getNAME() == null || model.getNAME().isBlank())
            throw new IllegalArgumentException("Model name is required");
        return model.getNAME() + ":" + (model.getVERSION() == null ? "" : model.getVERSION());
    }

    public String createModel(DATAMODEL model, String xml) {
        return persist(model, xml, true, false);
    }

    public String updateModel(String expectedKey, DATAMODEL model, String xml) {
        if (!keyFor(model).equals(expectedKey))
            throw new IllegalArgumentException(
                    "Model name and version cannot be changed during update");
        return persist(model, xml, false, true);
    }

    public String storeModel(DATAMODEL model, String xml) {
        return persist(model, xml, false, false);
    }

    private String persist(
            DATAMODEL model, String modelXml, boolean createOnly, boolean updateOnly) {
        if (model == null) throw new IllegalArgumentException("Model is required");

        String modelName = model.getNAME();
        String modelVersion = model.getVERSION() == null ? "" : model.getVERSION();
        String modelKey = keyFor(model);

        try (Session session = driver.session()) {
            session.executeWrite(
                    tx -> {
                        Map<String, Object> base = new HashMap<>();
                        base.put("modelKey", modelKey);
                        base.put("modelName", modelName);
                        base.put("modelVersion", modelVersion);
                        base.put("modelXml", modelXml == null ? "" : modelXml);
                        base.put(
                                "searchFields",
                                fr.expand.project.importdata.util.SearchIndex.fields(model));
                        base.put(
                                "searchRevision",
                                fr.expand.project.importdata.util.SearchIndex.revision(
                                        modelXml == null ? "" : modelXml));

                        var existing =
                                tx.run(
                                        "MATCH (m:DataModel {key:$modelKey}) SET m.key=m.key RETURN"
                                            + " m.name AS name,m.version AS version,m.xml AS xml",
                                        base);
                        boolean exists = existing.hasNext();
                        if (createOnly && exists)
                            throw new fr.expand.project.importdata.dao.StorageConflictException(
                                    "Model already exists: " + modelKey);
                        if (updateOnly && !exists)
                            throw new IllegalArgumentException("Model not found: " + modelKey);
                        Map<String, Object> before = null;
                        if (exists) {
                            var stored = existing.next();
                            before =
                                    Map.of(
                                            "key",
                                            modelKey,
                                            "name",
                                            stored.get("name").asString(),
                                            "version",
                                            stored.get("version").asString(""),
                                            "xml",
                                            stored.get("xml").asString(""));
                            String storedVersion =
                                    stored.get("version").isNull()
                                            ? ""
                                            : stored.get("version").asString();
                            if (!modelName.equals(stored.get("name").asString())
                                    || !modelVersion.equals(storedVersion))
                                throw new fr.expand.project.importdata.dao.StorageConflictException(
                                        "Model key collides with another name/version: "
                                                + modelKey);
                        }
                        // CREATE plus key uniqueness prevents concurrent create from silently
                        // replacing another schema.
                        tx.run(
                                (createOnly
                                                ? "CREATE (m:DataModel {key:$modelKey}) "
                                                : "MERGE (m:DataModel {key:$modelKey}) ")
                                        + "SET m.name=$modelName, m.version=$modelVersion, "
                                        + "m.xml=$modelXml, m.updatedAt=datetime()",
                                base);

                        tx.run(
                                "MATCH (n {modelKey:$modelKey}) WHERE n:ModelObjectType OR"
                                        + " n:ModelLinkType OR n:ModelAttribute DETACH DELETE n",
                                base);

                        Set<String> objectTypeNames = new HashSet<>();
                        if (model.getOBJECTTYPES() != null
                                && model.getOBJECTTYPES().getOBJECTTYPE() != null) {
                            for (OBJECTTYPE objectType : model.getOBJECTTYPES().getOBJECTTYPE()) {
                                if (objectType.getNAME() == null) {
                                    continue;
                                }
                                objectTypeNames.add(objectType.getNAME());
                                Map<String, Object> params = new HashMap<>(base);
                                params.put("typeName", objectType.getNAME());
                                params.put(
                                        "description",
                                        objectType.getDESCRIPTION() == null
                                                ? ""
                                                : objectType.getDESCRIPTION());

                                tx.run(
                                        "MERGE (o:ModelObjectType {modelKey:$modelKey,"
                                            + " name:$typeName}) SET o.description=$description",
                                        params);

                                tx.run(
                                        "MATCH (m:DataModel {key:$modelKey}) MATCH"
                                            + " (o:ModelObjectType {modelKey:$modelKey,"
                                            + " name:$typeName}) MERGE (m)-[:HAS_OBJECT_TYPE]->(o)",
                                        params);

                                if (objectType.getATTRIBUTEDEFINITIONS() != null
                                        && objectType
                                                        .getATTRIBUTEDEFINITIONS()
                                                        .getATTRIBUTEDEFINITION()
                                                != null) {
                                    for (ATTRIBUTEDEFINITION attribute :
                                            objectType
                                                    .getATTRIBUTEDEFINITIONS()
                                                    .getATTRIBUTEDEFINITION()) {
                                        if (attribute.getNAME() == null) {
                                            continue;
                                        }
                                        Map<String, Object> attrParams = new HashMap<>(params);
                                        attrParams.put(
                                                "attributeKey",
                                                modelKey
                                                        + "|OBJECT|"
                                                        + objectType.getNAME()
                                                        + "|"
                                                        + attribute.getNAME());
                                        attrParams.put("attributeName", attribute.getNAME());
                                        attrParams.put(
                                                "attributeType",
                                                attribute.getTYPE() == null
                                                        ? "STRING"
                                                        : attribute.getTYPE().value());
                                        attrParams.put("attributeRequired", attribute.isREQUIRED());
                                        attrParams.put(
                                                "attributeSearchable", attribute.isSEARCHABLE());
                                        attrParams.put(
                                                "attributeDefault",
                                                attribute.getDEFAULTVALUE() == null
                                                        ? ""
                                                        : attribute.getDEFAULTVALUE());
                                        attrParams.put(
                                                "attributeDescription",
                                                attribute.getDESCRIPTION() == null
                                                        ? ""
                                                        : attribute.getDESCRIPTION());

                                        tx.run(
                                                "CREATE (a:ModelAttribute {key:$attributeKey,"
                                                        + " modelKey:$modelKey, scope:'OBJECT',"
                                                        + " owner:$typeName, name:$attributeName,"
                                                        + " type:$attributeType,"
                                                        + " required:$attributeRequired,"
                                                        + " searchable:$attributeSearchable,"
                                                        + " defaultValue:$attributeDefault, "
                                                        + "description:$attributeDescription})",
                                                attrParams);

                                        tx.run(
                                                "MATCH (o:ModelObjectType {modelKey:$modelKey,"
                                                    + " name:$typeName}) MATCH (a:ModelAttribute"
                                                    + " {key:$attributeKey}) MERGE"
                                                    + " (o)-[:HAS_ATTRIBUTE]->(a)",
                                                attrParams);
                                    }
                                }
                            }
                        }

                        if (model.getOBJECTTYPES() != null
                                && model.getOBJECTTYPES().getOBJECTTYPE() != null) {
                            for (OBJECTTYPE objectType : model.getOBJECTTYPES().getOBJECTTYPE()) {
                                if (objectType.getNAME() == null
                                        || objectType.getPARENT() == null) {
                                    continue;
                                }
                                if (!objectTypeNames.contains(objectType.getPARENT())) {
                                    continue;
                                }
                                Map<String, Object> params = new HashMap<>(base);
                                params.put("childName", objectType.getNAME());
                                params.put("parentName", objectType.getPARENT());
                                tx.run(
                                        "MATCH (child:ModelObjectType {modelKey:$modelKey,"
                                                + " name:$childName}) MATCH (parent:ModelObjectType"
                                                + " {modelKey:$modelKey, name:$parentName}) MERGE"
                                                + " (child)-[:EXTENDS]->(parent)",
                                        params);
                            }
                        }

                        if (model.getLINKTYPES() != null
                                && model.getLINKTYPES().getLINKTYPE() != null) {
                            for (LINKTYPE linkType : model.getLINKTYPES().getLINKTYPE()) {
                                if (linkType.getNAME() == null) {
                                    continue;
                                }
                                Map<String, Object> params = new HashMap<>(base);
                                params.put("linkName", linkType.getNAME());
                                params.put(
                                        "description",
                                        linkType.getDESCRIPTION() == null
                                                ? ""
                                                : linkType.getDESCRIPTION());
                                params.put("directed", linkType.isDIRECTED());

                                tx.run(
                                        "MERGE (l:ModelLinkType {modelKey:$modelKey,"
                                            + " name:$linkName}) SET l.description=$description,"
                                            + " l.directed=$directed",
                                        params);

                                tx.run(
                                        "MATCH (m:DataModel {key:$modelKey}) MATCH (l:ModelLinkType"
                                                + " {modelKey:$modelKey, name:$linkName}) MERGE"
                                                + " (m)-[:HAS_LINK_TYPE]->(l)",
                                        params);

                                if (linkType.getSOURCETYPES() != null
                                        && linkType.getSOURCETYPES().getTYPEREF() != null) {
                                    for (TYPEREF typeRef : linkType.getSOURCETYPES().getTYPEREF()) {
                                        if (typeRef.getNAME() == null
                                                || !objectTypeNames.contains(typeRef.getNAME())) {
                                            continue;
                                        }
                                        Map<String, Object> relParams = new HashMap<>(params);
                                        relParams.put("objectType", typeRef.getNAME());
                                        tx.run(
                                                "MATCH (l:ModelLinkType {modelKey:$modelKey,"
                                                    + " name:$linkName}) MATCH (o:ModelObjectType"
                                                    + " {modelKey:$modelKey, name:$objectType})"
                                                    + " MERGE (l)-[:ALLOWS_SOURCE]->(o)",
                                                relParams);
                                    }
                                }

                                if (linkType.getTARGETTYPES() != null
                                        && linkType.getTARGETTYPES().getTYPEREF() != null) {
                                    for (TYPEREF typeRef : linkType.getTARGETTYPES().getTYPEREF()) {
                                        if (typeRef.getNAME() == null
                                                || !objectTypeNames.contains(typeRef.getNAME())) {
                                            continue;
                                        }
                                        Map<String, Object> relParams = new HashMap<>(params);
                                        relParams.put("objectType", typeRef.getNAME());
                                        tx.run(
                                                "MATCH (l:ModelLinkType {modelKey:$modelKey,"
                                                    + " name:$linkName}) MATCH (o:ModelObjectType"
                                                    + " {modelKey:$modelKey, name:$objectType})"
                                                    + " MERGE (l)-[:ALLOWS_TARGET]->(o)",
                                                relParams);
                                    }
                                }

                                if (linkType.getATTRIBUTEDEFINITIONS() != null
                                        && linkType.getATTRIBUTEDEFINITIONS()
                                                        .getATTRIBUTEDEFINITION()
                                                != null) {
                                    for (ATTRIBUTEDEFINITION attribute :
                                            linkType.getATTRIBUTEDEFINITIONS()
                                                    .getATTRIBUTEDEFINITION()) {
                                        if (attribute.getNAME() == null) {
                                            continue;
                                        }
                                        Map<String, Object> attrParams = new HashMap<>(params);
                                        attrParams.put(
                                                "attributeKey",
                                                modelKey
                                                        + "|LINK|"
                                                        + linkType.getNAME()
                                                        + "|"
                                                        + attribute.getNAME());
                                        attrParams.put("attributeName", attribute.getNAME());
                                        attrParams.put(
                                                "attributeType",
                                                attribute.getTYPE() == null
                                                        ? "STRING"
                                                        : attribute.getTYPE().value());
                                        attrParams.put("attributeRequired", attribute.isREQUIRED());
                                        attrParams.put(
                                                "attributeSearchable", attribute.isSEARCHABLE());
                                        attrParams.put(
                                                "attributeDefault",
                                                attribute.getDEFAULTVALUE() == null
                                                        ? ""
                                                        : attribute.getDEFAULTVALUE());
                                        attrParams.put(
                                                "attributeDescription",
                                                attribute.getDESCRIPTION() == null
                                                        ? ""
                                                        : attribute.getDESCRIPTION());

                                        tx.run(
                                                "CREATE (a:ModelAttribute {key:$attributeKey,"
                                                        + " modelKey:$modelKey, scope:'LINK',"
                                                        + " owner:$linkName, name:$attributeName,"
                                                        + " type:$attributeType,"
                                                        + " required:$attributeRequired,"
                                                        + " searchable:$attributeSearchable,"
                                                        + " defaultValue:$attributeDefault, "
                                                        + "description:$attributeDescription})",
                                                attrParams);

                                        tx.run(
                                                "MATCH (l:ModelLinkType {modelKey:$modelKey,"
                                                    + " name:$linkName}) MATCH (a:ModelAttribute"
                                                    + " {key:$attributeKey}) MERGE"
                                                    + " (l)-[:HAS_ATTRIBUTE]->(a)",
                                                attrParams);
                                    }
                                }
                            }
                        }

                        // Rebuild derived text in the same schema transaction, including inherited
                        // field changes.
                        tx.run(
                                        "MATCH (n:DataObject {modelKey:$modelKey}) SET"
                                                + " n.searchText="
                                                + fr.expand.project.importdata.util.SearchIndex
                                                        .EXPRESSION,
                                        base)
                                .consume();
                        tx.run(
                                        "MATCH (m:DataModel {key:$modelKey}) SET"
                                                + " m.searchRevision=$searchRevision",
                                        base)
                                .consume();
                        AuditTrail.record(
                                tx,
                                modelKey,
                                actor,
                                exists ? "UPDATE" : "CREATE",
                                "MODEL",
                                modelKey,
                                before,
                                Map.of(
                                        "key",
                                        modelKey,
                                        "name",
                                        modelName,
                                        "version",
                                        modelVersion,
                                        "xml",
                                        modelXml == null ? "" : modelXml));
                        return null;
                    });
        } catch (org.neo4j.driver.exceptions.ClientException e) {
            throw fr.expand.project.importdata.dao.StorageConflictException.translate(e);
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
            session.executeWrite(
                    tx -> {
                        Map<String, Object> params = new HashMap<>();
                        params.put("modelKey", modelKey);
                        // Acquire the same model lock as imports before inspecting/deleting its
                        // data.
                        var model =
                                tx.run(
                                        "MATCH (m:DataModel {key:$modelKey}) SET m.key=m.key RETURN"
                                                + " properties(m) AS p",
                                        params);
                        if (!model.hasNext()) return null;
                        Map<String, Object> before = new HashMap<>(model.single().get("p").asMap());
                        before.remove("searchRevision");
                        before.remove("updatedAt");
                        var counts =
                                tx.run(
                                                "MATCH (n:DataObject {modelKey:$modelKey}) OPTIONAL"
                                                    + " MATCH (n)-[r]->(:DataObject"
                                                    + " {modelKey:$modelKey}) RETURN count(DISTINCT"
                                                    + " n) AS objects,count(r) AS links",
                                                params)
                                        .single();
                        before.put("deletedObjectCount", counts.get("objects").asLong());
                        before.put("deletedLinkCount", counts.get("links").asLong());
                        tx.run("MATCH (n:DataObject {modelKey:$modelKey}) DETACH DELETE n", params);
                        tx.run(
                                "MATCH (n {modelKey:$modelKey}) WHERE n:ModelObjectType OR"
                                        + " n:ModelLinkType OR n:ModelAttribute DETACH DELETE n",
                                params);
                        tx.run("MATCH (m:DataModel {key:$modelKey}) DETACH DELETE m", params);
                        AuditTrail.record(
                                tx, modelKey, actor, "DELETE", "MODEL", modelKey, before, null);
                        return null;
                    });
        }
    }

    public String loadModelXmlByKey(String modelKey) {
        if (modelKey == null || modelKey.isBlank()) {
            return null;
        }
        try (Session session = driver.session()) {
            return session.executeRead(
                    tx -> {
                        Map<String, Object> params = new HashMap<>();
                        params.put("modelKey", modelKey);
                        var result =
                                tx.run(
                                        "MATCH (m:DataModel {key:$modelKey}) RETURN m.xml AS xml",
                                        params);
                        if (!result.hasNext()) {
                            return null;
                        }
                        var record = result.next();
                        return record.get("xml").isNull() ? null : record.get("xml").asString();
                    });
        }
    }

    public DATAMODEL loadModelByKey(String modelKey) {
        String xml = loadModelXmlByKey(modelKey);
        if (xml == null || xml.isBlank()) {
            return null;
        }
        try {
            return fr.expand.project.importdata.xml.XmlSupport.parseModel(xml);
        } catch (Exception e) {
            throw new RuntimeException("Unable to parse model XML", e);
        }
    }

    public java.util.List<Map<String, Object>> listModels() {
        try (Session session = driver.session()) {
            return session.executeRead(
                    tx -> {
                        var result =
                                tx.run(
                                        "MATCH (m:DataModel) RETURN m.key AS key, m.name AS name,"
                                                + " m.version AS version");
                        java.util.List<Map<String, Object>> models = new java.util.ArrayList<>();
                        while (result.hasNext()) {
                            var record = result.next();
                            Map<String, Object> row = new HashMap<>();
                            row.put("key", record.get("key").asString());
                            row.put("name", record.get("name").asString());
                            row.put(
                                    "version",
                                    record.get("version").isNull()
                                            ? ""
                                            : record.get("version").asString());
                            models.add(row);
                        }
                        return models;
                    });
        }
    }

    @Override
    public void close() {
        /* Shared driver is closed at application shutdown. */
    }
}
