package fr.expand.project.importdata.dao.connectors.impl;

import java.util.Map.Entry;

import org.neo4j.driver.internal.InternalNode;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Value;

import fr.expand.project.importdata.IConstantUtils;
import fr.expand.project.importdata.ObjectTypeEnum;
import fr.expand.project.importdata.dao.IConnectorDb;
import fr.expand.project.importdata.dto.ObjectToDbDto;

/**
 * Connector use to interact with Neo4J DB
 * 
 * @author Maxime
 *
 */
public class Neo4jConnector implements IConnectorDb {

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

	@Override
	public int writeObject(ObjectToDbDto object) {
		if (object != null) {
			// Generate request for DB
			String request = "CREATE (a:" + convertObjectForDb(object) + ") RETURN ID(a)";
			System.out.println(request);
			int newId = launchCreationRequest(request, true);
			object.setId(newId);
			return newId;
		}
		System.err.println("Object is null");
		return -1;
	}

	/**
	 * Launch Cypher Request
	 * 
	 * @param request
	 * @param resultAttempted
	 *            : true if we try to get a returned ID
	 * @return ID or -1
	 */
	private int launchCreationRequest(String request, boolean resultAttempted) {
		Session session = null;
		try {
			// Create Sessions
			session = createSession();
			// Launch request
			StatementResult result = session.run(request);
			if (!resultAttempted) {
				return -1;
			}
			Record record = result.single();
			Value value = record.values().get(0);
			int id = value.asInt();
			return id;
		} finally {
			closeSession(session);
		}
	}

	@Override
	public int writeLink(ObjectToDbDto objectA, ObjectToDbDto objectB, boolean isOriented) {
		String request = "MATCH (a:" + objectA.getType().toString() + ") WHERE ID(a)=" + objectA.getId() + " "
				+ "MATCH (b:" + objectB.getType().toString() + ") WHERE ID(b)=" + objectB.getId() + " "
				+ "CREATE (a)-[:KNOWS]->(b)";
		System.out.println(request);
		return launchCreationRequest(request, false);
	}

	/**
	 * Convert Object to string representation use by Cypher request
	 * 
	 * @param object
	 * @return
	 */
	private String convertObjectForDb(ObjectToDbDto object) {
		StringBuilder result = new StringBuilder();
		result.append(object.getType()).append(IConstantUtils.SPACE);
		if (!object.getAttributes().isEmpty()) {
			boolean isFirst = true;
			result.append("{");
			for (Entry<String, Object> entry : object.getAttributes().entrySet()) {
				if (!isFirst) {
					result.append(",");
				} else {
					isFirst = false;
				}
				result.append(entry.getKey()).append(":'").append(entry.getValue()).append("'");
			}
			result.append("}");
		}
		return result.toString();
	}

	@Override
	public ObjectToDbDto getObjectToDbDto(ObjectTypeEnum typeObject, int idObject) {
		Session session = null;
		try {
			session = createSession();
			String request = "MATCH (n:" + typeObject.toString() + ") WHERE ID(n)=" + idObject
					+ " RETURN n AS TAILLE LIMIT 5";
			System.out.println(request);
			StatementResult result = session.run(request);
			return convertResultToObjectToDb(typeObject, result.single());
		} finally {
			closeSession(session);
		}
	}

	/**
	 * Convert Cypher request result to Java object
	 * 
	 * @param typeObject
	 * 
	 * @param object
	 * @return
	 */
	private ObjectToDbDto convertResultToObjectToDb(ObjectTypeEnum typeObject, Record record) {
		ObjectToDbDto result = new ObjectToDbDto(typeObject);
		for (Entry<String, Object> entry : record.asMap().entrySet()) {
			if (entry.getValue() instanceof InternalNode) {
				for (Entry<String, Object> entryInternalNode : ((InternalNode) entry.getValue()).asMap().entrySet()) {
					System.out.println(entryInternalNode.getKey() + " " + entryInternalNode.getValue());
					result.getAttributes().put(entryInternalNode.getKey(), entryInternalNode.getValue());
				}
			} else {
				System.out.println(entry.getKey() + " " + entry.getValue());
				result.getAttributes().put(entry.getKey(), entry.getValue());
			}
		}
		return result;
	}

}
