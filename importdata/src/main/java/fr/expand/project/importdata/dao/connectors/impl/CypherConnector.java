package fr.expand.project.importdata.dao.connectors.impl;

import fr.expand.project.commons.ObjectTypeEnum;
import fr.expand.project.importdata.audit.*;
import fr.expand.project.importdata.dao.*;
import fr.expand.project.importdata.dto.*;
import fr.expand.project.importdata.dto.generated.*;
import fr.expand.project.importdata.model.ModelManager;
import fr.expand.project.importdata.util.CypherUtils;
import fr.expand.project.importdata.util.SearchIndex;

import org.neo4j.driver.*;
import org.neo4j.driver.types.Node;

import java.util.*;

public class CypherConnector extends IConnectorDb {
    private Driver driver;
    private final AuditActor actor;

    public CypherConnector() {
        this(AuditActor.system());
    }

    public CypherConnector(AuditActor actor) {
        this.actor = Objects.requireNonNull(actor);
    }

    @Override
    protected void connectToDb() {
        driver = Neo4jDriverProvider.getDriver();
    }

    @Override
    public void closeConnection() {
        /* Driver lifecycle belongs to application. */
    }

    @Override
    public int writeObject(DataPackObject object) {
        Objects.requireNonNull(object, "object");
        try (Session session = driver.session()) {
            int id = session.executeWrite(tx -> createObject(tx, object, modelKey));
            object.setInternalId(id);
            return id;
        } catch (org.neo4j.driver.exceptions.ClientException e) {
            throw StorageConflictException.translate(e);
        }
    }

    private int createObject(TransactionContext tx, DataPackObject object, String key) {
        Map<String, List<String>> fields = Map.of();
        if (key != null && !key.isBlank()) {
            var model =
                    tx.run(
                            "MATCH (m:DataModel {key:$key}) SET m.key=m.key RETURN m.xml AS xml",
                            Map.of("key", key));
            if (model.hasNext())
                fields = SearchIndex.fieldsFromXml(model.single().get("xml").asString(""));
        }
        return createObject(tx, object, key, fields);
    }

    private int createObject(
            TransactionContext tx,
            DataPackObject object,
            String key,
            Map<String, List<String>> fields) {
        Map<String, Object> properties = CypherUtils.properties(object, key);
        properties.put("searchText", SearchIndex.text(object.getTYPE(), properties, fields));
        if (key != null && !key.isBlank()) {
            boolean exists =
                    tx.run(
                                    "MATCH (n:DataObject {modelKey:$key}) WHERE n.dataId=$id AND"
                                        + " (n.type=$type OR $type IN labels(n)) RETURN n LIMIT 1",
                                    Map.of(
                                            "key",
                                            key,
                                            "id",
                                            object.getID(),
                                            "type",
                                            object.getTYPE()))
                            .hasNext();
            if (exists)
                throw new StorageConflictException(
                        "Object already exists: " + object.getTYPE() + "/" + object.getID());
        }
        String labels = CypherUtils.identifier(object.getTYPE()) + ":DataObject";
        long id =
                tx.run(
                                "CREATE (n:" + labels + ") SET n=$properties RETURN id(n) AS id",
                                Map.of("properties", properties))
                        .single()
                        .get("id")
                        .asLong();
        if (key != null && !key.isBlank()) {
            var after = AuditTrail.object(tx, key, id);
            AuditTrail.record(
                    tx, key, actor, "CREATE", "OBJECT", AuditTrail.identity(after), null, after);
        }
        return Math.toIntExact(id);
    }

    @Override
    public int writeLink(DataPackObject a, DataPackObject b, boolean directed, String linkType) {
        try (Session session = driver.session()) {
            return session.executeWrite(
                    tx -> createLink(tx, a, b, directed, linkType, List.of(), modelKey));
        }
    }

    private int createLink(
            TransactionContext tx,
            DataPackObject a,
            DataPackObject b,
            boolean directed,
            String type,
            List<ATTRIBUTE> attributes,
            String key) {
        String relType = type == null || type.isBlank() ? "KNOWS" : type;
        Map<String, Object> params = new HashMap<>();
        params.put("key", key);
        params.put("a", a.getInternalId() == null ? a.getID() : a.getInternalId());
        params.put("b", b.getInternalId() == null ? b.getID() : b.getInternalId());
        params.put("aType", a.getTYPE());
        params.put("bType", b.getTYPE());
        Map<String, Object> props = CypherUtils.attributes(attributes);
        props.put("linkType", relType);
        props.put("directed", directed);
        props.put("uuid", UUID.randomUUID().toString());
        if (key != null && !key.isBlank()) props.put("modelKey", key);
        params.put("properties", props);
        String matchA = a.getInternalId() == null ? "a.dataId=$a" : "id(a)=$a";
        String matchB = b.getInternalId() == null ? "b.dataId=$b" : "id(b)=$b";
        var result =
                tx.run(
                        "MATCH (a:"
                                + CypherUtils.identifier(a.getTYPE())
                                + "),(b:"
                                + CypherUtils.identifier(b.getTYPE())
                                + ") WHERE "
                                + matchA
                                + " AND "
                                + matchB
                                + " AND ($key IS NULL OR (a.modelKey=$key AND b.modelKey=$key))"
                                + " CREATE (a)-[r:"
                                + CypherUtils.identifier(relType)
                                + "]->(b) SET r=$properties RETURN id(r) AS id",
                        params);
        if (!result.hasNext()) throw new IllegalArgumentException("Link endpoint does not exist");
        int id = Math.toIntExact(result.next().get("id").asLong());
        if (result.hasNext()) throw new IllegalArgumentException("Ambiguous link endpoint");
        if (key != null && !key.isBlank()) {
            var after = AuditTrail.link(tx, key, id);
            AuditTrail.record(
                    tx, key, actor, "CREATE", "LINK", AuditTrail.identity(after), null, after);
        }
        return id;
    }

    /** Every object and relationship in one transaction; any failure rolls the entire pack back. */
    @Override
    public void importData(DATAS data, String key, ModelManager manager) {
        if (key == null || key.isBlank())
            throw new IllegalArgumentException("A model key is required");
        try (Session session = driver.session()) {
            session.executeWrite(
                    tx -> {
                        // Lock the model for the duration of the import so validation cannot race
                        // schema replacement.
                        var schema =
                                tx.run(
                                        "MATCH (m:DataModel {key:$key}) SET m.key=m.key RETURN"
                                                + " m.xml AS xml",
                                        Map.of("key", key));
                        if (!schema.hasNext())
                            throw new StorageConflictException("Model no longer exists: " + key);
                        var schemaRecord = schema.single();
                        String storedXml =
                                schemaRecord.get("xml").isNull()
                                        ? ""
                                        : schemaRecord.get("xml").asString();
                        String validatedXml = manager.getCurrentModelXml();
                        if (validatedXml != null && !validatedXml.equals(storedXml))
                            throw new StorageConflictException(
                                    "Model changed during validation; reload and retry the import");
                        Map<String, List<String>> searchFields =
                                SearchIndex.fields(manager.getCurrentModel());
                        Map<String, DataPackObject> objects = new HashMap<>();
                        if (data.getOBJECTS() != null)
                            for (OBJECT input : data.getOBJECTS().getOBJECT()) {
                                DataPackObject object = new DataPackObject();
                                object.setID(input.getID());
                                object.setTYPE(input.getTYPE());
                                object.getATTRIBUTE().addAll(input.getATTRIBUTE());
                                object.setInternalId(createObject(tx, object, key, searchFields));
                                if (objects.put(input.getTYPE() + "/" + input.getID(), object)
                                        != null)
                                    throw new IllegalArgumentException("Duplicate object identity");
                            }
                        if (data.getLINKS() != null)
                            for (LINK link : data.getLINKS().getLINK()) {
                                var a =
                                        objects.get(
                                                link.getOBJLINKA().getTYPE()
                                                        + "/"
                                                        + link.getOBJLINKA().getID());
                                var b =
                                        objects.get(
                                                link.getOBJLINKB().getTYPE()
                                                        + "/"
                                                        + link.getOBJLINKB().getID());
                                if (a == null || b == null)
                                    throw new IllegalArgumentException(
                                            "Link endpoints must be included in the import");
                                var definition = manager.getLinkType(link.getTYPE());
                                boolean directed = definition == null || definition.isDIRECTED();
                                createLink(
                                        tx,
                                        a,
                                        b,
                                        directed,
                                        link.getTYPE(),
                                        link.getATTRIBUTE(),
                                        key);
                            }
                        AuditTrail.record(
                                tx,
                                key,
                                actor,
                                "IMPORT",
                                "IMPORT",
                                actor.operationId(),
                                null,
                                Map.of(
                                        "format",
                                        "XML",
                                        "objectCount",
                                        objects.size(),
                                        "linkCount",
                                        data.getLINKS() == null
                                                ? 0
                                                : data.getLINKS().getLINK().size()));
                        return null;
                    });
        } catch (org.neo4j.driver.exceptions.ClientException e) {
            throw StorageConflictException.translate(e);
        }
    }

    @Override
    public DataPackObject getObjectToDbDto(ObjectTypeEnum type, int id) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        params.put("key", modelKey);
        try (Session session = driver.session()) {
            return session.executeRead(
                    tx -> {
                        var result =
                                tx.run(
                                        "MATCH (n:"
                                                + CypherUtils.identifier(type.toString())
                                                + ") WHERE id(n)=$id AND ($key IS NULL OR"
                                                + " n.modelKey=$key) RETURN n",
                                        params);
                        if (!result.hasNext()) return null;
                        Node node = result.single().get("n").asNode();
                        DataPackObject object = new DataPackObject();
                        object.setTYPE(type.toString());
                        object.setID(node.get("dataId").isNull() ? id : node.get("dataId").asInt());
                        object.setInternalId(id);
                        node.asMap()
                                .forEach(
                                        (k, v) -> {
                                            if (!CypherUtils.isReservedProperty(k))
                                                object.getATTRIBUTE()
                                                        .add(
                                                                new DataPackAttribute(
                                                                        k, String.valueOf(v)));
                                        });
                        return object;
                    });
        }
    }

    @Override
    public void deleteAll() {
        try (Session session = driver.session()) {
            if (modelKey == null || modelKey.isBlank())
                session.executeWrite(
                        tx -> {
                            tx.run("MATCH (n:DataObject) DETACH DELETE n").consume();
                            return null;
                        });
            else
                session.executeWrite(
                        tx -> {
                            tx.run(
                                            "MATCH (n:DataObject {modelKey:$key}) DETACH DELETE n",
                                            Map.of("key", modelKey))
                                    .consume();
                            return null;
                        });
        }
    }
}
