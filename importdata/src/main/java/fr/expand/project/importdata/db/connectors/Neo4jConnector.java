package fr.expand.project.importdata.db.connectors;

import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;

/**
 * Connector use to interact with Neo4J DB
 * 
 * @author Maxime
 *
 */
public class Neo4jConnector {

	private Driver driver;

	/**
	 * Create Graph DB session
	 * 
	 * @return
	 */
	private Session createSession() {
		driver = GraphDatabase.driver("bolt://localhost", AuthTokens.basic("neo4j", "expand"));
		return driver.session();
	}

	/**
	 * Close Graph DB session
	 * 
	 * @param session
	 */
	private void closeSession(Session session) {
		if (session != null && session.isOpen()) {
			session.close();
		}
		if (driver != null) {
			driver.close();
		}
	}

	/**
	 * Run request to create a new node
	 * 
	 * @param session
	 */
	private void createDataSample(Session session) {
		session.run("CREATE (a:Person {name:'Arthur', title:'King'})");
	}

	/**
	 * Run request to get nodes
	 * 
	 * @param session
	 * @return
	 */
	private StatementResult requestDataSample(Session session) {
		return session.run("MATCH (a:Person) WHERE a.name = 'Arthur' RETURN a.name AS name, a.title AS title");
	}

	/**
	 * Read request result
	 * 
	 * @param result
	 */
	private void readResultSample(StatementResult result) {
		while (result.hasNext()) {
			Record record = result.next();
			System.out.println(record.get("title").asString() + " " + record.get("name").asString());
		}
	}

	/**
	 * Simple test method
	 */
	public void testNeo4j() {
		Session session = null;
		try {
			session = createSession();
			createDataSample(session);
			StatementResult result = requestDataSample(session);
			readResultSample(result);
		} finally {
			closeSession(session);
		}

	}

}
