package fr.expand.project.importdata;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import fr.expand.project.commons.ObjectTypeEnum;
import fr.expand.project.importdata.dao.IConnectorDb;
import fr.expand.project.importdata.dao.connectors.impl.CypherConnector;
import fr.expand.project.importdata.dao.connectors.impl.Neo4jConnector;
import fr.expand.project.importdata.dto.DataPackObject;

public class ConnectorTests {

	private static final Logger LOGGER = LogManager.getLogger();

	@Test
	public void test() {
		LOGGER.info("Connexion to graph db");
		try {
			IConnectorDb connector = new CypherConnector();
			testConnector(connector);

			connector = new Neo4jConnector();
			testConnector(connector);
		} catch (Exception e) {
			Assert.fail(e.getStackTrace().toString());
		}
		Assert.assertTrue(true);
	}

	private void testConnector(IConnectorDb connector) {

		LOGGER.info("TEST for CONNECTOR : " + connector.getClass().toString());

		// Create a new Node
		DataPackObject objectA = new DataPackObject(ObjectTypeEnum.HUMAIN);
		objectA.getAttributes().put("TAILLE", "1m77");
		objectA.getAttributes().put("POIDS", "71");
		int idObjA = connector.writeObject(objectA);
		LOGGER.info("Create new Node with id " + idObjA);

		// Create a new Node
		DataPackObject objectB = new DataPackObject(ObjectTypeEnum.HUMAIN);
		objectB.getAttributes().put("TAILLE", "1m60");
		objectB.getAttributes().put("POIDS", "54");
		int idObjB = connector.writeObject(objectB);
		LOGGER.info("Create new Node with id " + idObjB);

		// Create a new link
		int idLink = connector.writeLink(objectA, objectB, true);
		LOGGER.info("Create new link with id " + idObjB);

		// Get a node from DB
		DataPackObject objectFromDb = connector.getObjectToDbDto(ObjectTypeEnum.HUMAIN, idObjA);
	}
}
