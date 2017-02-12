package fr.expand.project.importdata;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import fr.expand.project.commons.ObjectTypeEnum;
import fr.expand.project.importdata.dao.IConnectorDb;
import fr.expand.project.importdata.dao.connectors.impl.CypherConnector;
import fr.expand.project.importdata.dao.connectors.impl.Neo4jConnector;
import fr.expand.project.importdata.dto.generated.DataPackAttribute;
import fr.expand.project.importdata.dto.generated.DataPackObject;

public class ConnectorTests {

	private static final Logger LOGGER = LogManager.getLogger();

	@Test
	public void test() {
		LOGGER.info("Connexion to graph db");
		try {
			IConnectorDb connector = new CypherConnector();
			testConnector(connector);
			connector.deleteAll();

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
		DataPackObject objectA = new DataPackObject();
		objectA.setTYPE(ObjectTypeEnum.HUMAIN.toString());
		objectA.getATTRIBUTE().add(new DataPackAttribute("PRENOM", "Stan"));
		objectA.getATTRIBUTE().add(new DataPackAttribute("NOM", "Marsh"));
		objectA.getATTRIBUTE().add(new DataPackAttribute("TAILLE", "1m20"));
		objectA.getATTRIBUTE().add(new DataPackAttribute("POIDS", "35kg"));
		int idObjA = connector.writeObject(objectA);
		LOGGER.info("Create new Node with id " + idObjA);

		// Create a new Node
		DataPackObject objectB = new DataPackObject();
		objectB.setTYPE(ObjectTypeEnum.HUMAIN.toString());
		objectB.getATTRIBUTE().add(new DataPackAttribute("PRENOM", "Eric"));
		objectB.getATTRIBUTE().add(new DataPackAttribute("NOM", "Cartman"));
		objectB.getATTRIBUTE().add(new DataPackAttribute("TAILLE", "1m24"));
		objectB.getATTRIBUTE().add(new DataPackAttribute("POIDS", "65kg"));

		int idObjB = connector.writeObject(objectB);
		LOGGER.info("Create new Node with id " + idObjB);

		// Create a new link
		connector.writeLink(objectA, objectB, true);
		LOGGER.info("Create new link with id " + idObjB);

		// Get a node from DB
		DataPackObject objectFromDb = connector.getObjectToDbDto(ObjectTypeEnum.HUMAIN, idObjA);
	}
}
