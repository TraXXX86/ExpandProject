package fr.expand.project.importdata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import fr.expand.project.commons.ObjectTypeEnum;
import fr.expand.project.importdata.dao.Neo4jDriverProvider;
import fr.expand.project.importdata.dao.StorageConflictException;
import fr.expand.project.importdata.dao.connectors.impl.CypherConnector;
import fr.expand.project.importdata.dto.DataPackAttribute;
import fr.expand.project.importdata.dto.DataPackObject;
import fr.expand.project.importdata.model.Neo4jModelStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.driver.Session;

import java.util.Map;
import java.util.UUID;

/** Neo4j integration test confined to unique model keys and safe targeted cleanup. */
public class ConnectorIT {
    private String modelKey;
    private String siblingModelKey;
    private CypherConnector connector;

    @Before
    public void setUp() {
        String id = UUID.randomUUID().toString().replace("-", "");
        modelKey = "ConnectorIT_" + id + ":1";
        siblingModelKey = "ConnectorIT_" + id + "_sibling:1";
        connector = new CypherConnector();
        connector.setModelKey(modelKey);
    }

    @After
    public void tearDown() {
        if (connector != null) {
            connector.close();
        }
        Neo4jModelStore models = new Neo4jModelStore();
        models.deleteModelAndDataByKey(modelKey);
        models.deleteModelAndDataByKey(siblingModelKey);
    }

    @Test
    public void writesReadsAndLinksObjectsWithinOneModel() {
        DataPackObject stan = person(1, "Stan", "Marsh");
        DataPackObject cartman = person(2, "Eric", "Cartman");
        int stanNodeId = connector.writeObject(stan);
        int cartmanNodeId = connector.writeObject(cartman);

        assertEquals(Integer.valueOf(stanNodeId), stan.getInternalId());
        assertEquals(Integer.valueOf(cartmanNodeId), cartman.getInternalId());
        connector.writeLink(stan, cartman, true, "KNOWS");

        DataPackObject loaded = connector.getObjectToDbDto(ObjectTypeEnum.HUMAIN, stanNodeId);
        assertNotNull(loaded);
        assertEquals("HUMAIN", loaded.getTYPE());
        assertEquals(1, loaded.getID());
        assertEquals(
                "Stan",
                loaded.getATTRIBUTE().stream()
                        .filter(attribute -> "PRENOM".equals(attribute.getKEY()))
                        .findFirst()
                        .orElseThrow()
                        .getVALUE());

        try (Session session = Neo4jDriverProvider.getDriver().session()) {
            long count =
                    session.run(
                                    "MATCH (a:DataObject {modelKey:$key, dataId:1})-[r:KNOWS"
                                        + " {modelKey:$key}]->(b:DataObject {modelKey:$key,"
                                        + " dataId:2}) RETURN count(r) AS count",
                                    Map.of("key", modelKey))
                            .single()
                            .get("count")
                            .asLong();
            assertEquals(1L, count);
        }
    }

    @Test
    public void duplicateIdentityIsRejectedOnlyInsideItsModel() {
        DataPackObject original = person(7, "Stan", "Marsh");
        connector.writeObject(original);
        assertThrows(
                StorageConflictException.class,
                () -> connector.writeObject(person(7, "Another", "Person")));

        try (CypherConnector sibling = new CypherConnector()) {
            sibling.setModelKey(siblingModelKey);
            DataPackObject independent = person(7, "Independent", "Model");
            int nodeId = sibling.writeObject(independent);
            assertNotNull(sibling.getObjectToDbDto(ObjectTypeEnum.HUMAIN, nodeId));
        }
    }

    private static DataPackObject person(int id, String firstName, String lastName) {
        DataPackObject object = new DataPackObject();
        object.setID(id);
        object.setTYPE(ObjectTypeEnum.HUMAIN.toString());
        object.getATTRIBUTE().add(new DataPackAttribute("PRENOM", firstName));
        object.getATTRIBUTE().add(new DataPackAttribute("NOM", lastName));
        return object;
    }
}
