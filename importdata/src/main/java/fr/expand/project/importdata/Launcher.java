package fr.expand.project.importdata;

import fr.expand.project.commons.ObjectTypeEnum;
import fr.expand.project.importdata.dao.connectors.impl.CypherConnector;
import fr.expand.project.importdata.dto.ObjectToDbDto;

public class Launcher {
	public static void main(String[] args) {
		System.out.println("Connexion to graph db");
		CypherConnector connector = new CypherConnector();

		// Create a new Node
		ObjectToDbDto objectA = new ObjectToDbDto(ObjectTypeEnum.HUMAIN);
		objectA.getAttributes().put("TAILLE", "1m77");
		objectA.getAttributes().put("POIDS", "71");
		int idObjA = connector.writeObject(objectA);
		System.out.println("Create new Node with id " + idObjA);

		// Create a new Node
		ObjectToDbDto objectB = new ObjectToDbDto(ObjectTypeEnum.HUMAIN);
		objectB.getAttributes().put("TAILLE", "1m60");
		objectB.getAttributes().put("POIDS", "54");
		int idObjB = connector.writeObject(objectB);
		System.out.println("Create new Node with id " + idObjB);

		// Create a new link
		int idLink = connector.writeLink(objectA, objectB, true);
		System.out.println("Create new link with id " + idObjB);

		// Get a node from DB
		ObjectToDbDto objectFromDb = connector.getObjectToDbDto(ObjectTypeEnum.HUMAIN, idObjA);
	}
}
