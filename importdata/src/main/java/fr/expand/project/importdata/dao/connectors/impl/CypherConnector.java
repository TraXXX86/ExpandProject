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

import fr.expand.project.commons.ObjectTypeEnum;
import fr.expand.project.importdata.dao.IConnectorDb;
import fr.expand.project.importdata.dto.generated.DataPackAttribute;
import fr.expand.project.importdata.dto.generated.DataPackObject;
import fr.expand.project.importdata.util.CypherUtils;

/**
 * Connector use to interact with Neo4J DB
 * 
 * @author Maxime
 *
 */
public class CypherConnector extends IConnectorDb {

	private Driver driver;
	private Session session;

	// ############################# Start/Close Connection to DB methods

	@Override
	protected void connectToDb() {
		driver = GraphDatabase.driver("bolt://localhost", AuthTokens.basic("neo4j", "expand"));
		session = driver.session();
	}

	@Override
	public void closeConnection() {
		if (session != null && session.isOpen()) {
			session.close();
		}
		if (driver != null) {
			driver.close();
		}
	}

	// ############################# Request methods

	@Override
	public int writeObject(DataPackObject object) {
		if (object != null) {
			// Generate request for DB
			String request = "CREATE (a:" + CypherUtils.convertObjectForDb(object) + ") RETURN ID(a)";
			System.out.println(request);
			int newId = launchCreationRequest(request, true);
			object.setID(newId);
			return newId;
		}
		System.err.println("Object is null");
		return -1;
	}

	@Override
	public int writeLink(DataPackObject objectA, DataPackObject objectB, boolean isOriented) {
		String request = "MATCH (a:" + objectA.getTYPE() + ") WHERE ID(a)=" + objectA.getID() + " " + "MATCH (b:"
				+ objectB.getTYPE() + ") WHERE ID(b)=" + objectB.getID() + " " + "CREATE (a)-[:KNOWS]->(b)";
		System.out.println(request);
		return launchCreationRequest(request, false);
	}

	@Override
	public DataPackObject getObjectToDbDto(ObjectTypeEnum typeObject, int idObject) {
		String request = "MATCH (n:" + typeObject.toString() + ") WHERE ID(n)=" + idObject
				+ " RETURN n AS TAILLE LIMIT 5";
		System.out.println(request);
		StatementResult result = session.run(request);
		return convertResultToObjectToDb(typeObject, result.single());
	}

	// ############################# Utils methods

	/**
	 * Launch Cypher Request
	 * 
	 * @param request
	 * @param resultAttempted
	 *            : true if we try to get a returned ID
	 * @return ID or -1
	 */
	private int launchCreationRequest(String request, boolean resultAttempted) {
		// Launch request
		StatementResult result = session.run(request);
		if (!resultAttempted) {
			return -1;
		}
		Record record = result.single();
		Value value = record.values().get(0);
		int id = value.asInt();
		return id;
	}

	/**
	 * Convert Cypher request result to Java object
	 * 
	 * @param typeObject
	 * 
	 * @param object
	 * @return
	 */
	private DataPackObject convertResultToObjectToDb(ObjectTypeEnum typeObject, Record record) {
		DataPackObject result = new DataPackObject();
		result.setTYPE(typeObject.toString());
		for (Entry<String, Object> entry : record.asMap().entrySet()) {
			if (entry.getValue() instanceof InternalNode) {
				for (Entry<String, Object> entryInternalNode : ((InternalNode) entry.getValue()).asMap().entrySet()) {
					System.out.println(entryInternalNode.getKey() + " " + entryInternalNode.getValue());
					if (entryInternalNode.getValue() instanceof String) {
						DataPackAttribute attribute = new DataPackAttribute();
						attribute.setKEY(entryInternalNode.getKey());
						attribute.setVALUE((String) entryInternalNode.getValue());
						result.getATTRIBUTES().add(attribute);
					}
				}
			} else {
				System.out.println(entry.getKey() + " " + entry.getValue());
				if (entry.getValue() instanceof String) {
					DataPackAttribute attribute = new DataPackAttribute();
					attribute.setKEY(entry.getKey());
					attribute.setVALUE((String) entry.getValue());
					result.getATTRIBUTES().add(attribute);
				}
			}
		}
		return result;
	}

}