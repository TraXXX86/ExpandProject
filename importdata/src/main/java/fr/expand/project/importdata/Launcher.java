package fr.expand.project.importdata;

import fr.expand.project.importdata.db.connectors.Neo4jConnector;

public class Launcher {
	public static void main(String[] args) {
		System.out.println("Connexion to graph db");
		Neo4jConnector connector = new Neo4jConnector();
		connector.testNeo4j();
	}
}
