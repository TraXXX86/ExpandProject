package fr.expand.project.importdata.dao.connectors.impl;

import java.util.Map.Entry;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;

import fr.expand.project.commons.ObjectTypeEnum;
import fr.expand.project.importdata.config.Neo4jConfig;
import fr.expand.project.importdata.dao.IConnectorDb;
import fr.expand.project.importdata.dto.DataPackAttribute;
import fr.expand.project.importdata.dto.DataPackObject;
import fr.expand.project.importdata.util.CypherUtils;

/**
 * Connector use to interact with Neo4J DB
 * 
 * @author Maxime
 *
 */
public class CypherConnector extends IConnectorDb {

	private final Neo4jConfig neo4jConfig;
	private Driver driver;
	private Session session;

	public CypherConnector() {
		this(Neo4jConfig.fromSystem());
	}

	public CypherConnector(Neo4jConfig neo4jConfig) {
		super(false);
		this.neo4jConfig = neo4jConfig == null ? Neo4jConfig.fromSystem() : neo4jConfig;
		connectToDb();
	}

	// ############################# Start/Close Connection to DB methods

	@Override
	protected void connectToDb() {
		driver = neo4jConfig.createDriver();
		session = driver.session();
	}

	@Override
	public void closeConnection() {
		if (session != null) {
			session.close();
			session = null;
		}
		if (driver != null) {
			driver.close();
			driver = null;
		}
	}

	// ############################# Request methods

	@Override
	public int writeObject(DataPackObject object) {
		if (object != null) {
			// Generate request for DB
			String request = "CREATE (a:" + CypherUtils.convertObjectForDb(object, modelKey) + ") RETURN ID(a)";
			LOGGER.info(request);
			int newId = launchCreationRequest(request, true);
			if (object.getID() <= 0) {
				object.setID(newId);
			}
			object.setInternalId(newId);
			return newId;
		}
		LOGGER.error("Object is null");
		return -1;
	}

	@Override
    public int writeLink(DataPackObject objectA, DataPackObject objectB, boolean isOriented, String linkType) {
        String relationType = normalizeRelationshipType(linkType);
        String relationProperties = buildRelationProperties(linkType);
        String matchA = buildNodeMatch(objectA, "a");
        String matchB = buildNodeMatch(objectB, "b");
        String request = matchA + " " + matchB + " CREATE (a)-[:"
                + relationType + relationProperties + "]->(b)";
        LOGGER.info(request);
        return launchCreationRequest(request, false);
    }

	@Override
	public DataPackObject getObjectToDbDto(ObjectTypeEnum typeObject, int idObject) {
		String request = "MATCH (n:" + typeObject.toString() + ") WHERE ID(n)=" + idObject
				+ " RETURN n AS TAILLE LIMIT 5";
		LOGGER.info(request);
		Result result = session.run(request);
		return convertResultToObjectToDb(typeObject, result.single());
	}

	@Override
	public void deleteAll() {
		closeConnection();
		connectToDb();

		// Create query
		String request = "MATCH (n) DETACH DELETE n";
		LOGGER.info(request);

		// Launch request
		session.run(request);
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
		Result result = session.run(request);
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
			if (entry.getValue() instanceof Node) {
				for (Entry<String, Object> entryInternalNode : ((Node) entry.getValue()).asMap().entrySet()) {
					LOGGER.info(entryInternalNode.getKey() + " " + entryInternalNode.getValue());
					if (entryInternalNode.getValue() instanceof String) {
						DataPackAttribute attribute = new DataPackAttribute(entryInternalNode.getKey(),
								(String) entryInternalNode.getValue());
						result.getATTRIBUTE().add(attribute);
					}
				}
			} else {
				LOGGER.info(entry.getKey() + " " + entry.getValue());
				if (entry.getValue() instanceof String) {
					DataPackAttribute attribute = new DataPackAttribute(entry.getKey(), (String) entry.getValue());
					result.getATTRIBUTE().add(attribute);
				}
			}
		}
		return result;
	}

    private String buildRelationProperties(String linkType) {
        boolean hasModel = modelKey != null && !modelKey.isBlank();
        boolean hasLinkType = linkType != null && !linkType.isBlank();
        if (!hasModel && !hasLinkType) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(" {");
        boolean first = true;
        if (hasModel) {
            builder.append("modelKey:'").append(modelKey).append("'");
            first = false;
        }
        if (hasLinkType) {
            if (!first) {
                builder.append(",");
            }
            builder.append("linkType:'").append(linkType).append("'");
        }
        builder.append("}");
        return builder.toString();
    }

    private String normalizeRelationshipType(String linkType) {
        if (linkType == null || linkType.isBlank()) {
            return "KNOWS";
        }
        String sanitized = linkType.trim().replaceAll("[^A-Za-z0-9_]", "_");
        if (sanitized.isEmpty()) {
            return "KNOWS";
        }
        return sanitized.toUpperCase();
    }

    private String buildNodeMatch(DataPackObject object, String alias) {
        StringBuilder builder = new StringBuilder();
        builder.append("MATCH (").append(alias).append(":").append(object.getTYPE()).append(")");

        Integer internalId = object.getInternalId();
        boolean useInternal = internalId != null && internalId > 0 && object.getID() == internalId;
        boolean hasDataId = object.getID() > 0 && !useInternal;

        boolean hasWhere = false;
        if (useInternal) {
            builder.append(" WHERE ID(").append(alias).append(")=").append(internalId);
            hasWhere = true;
        } else if (hasDataId) {
            builder.append(" WHERE ").append(alias).append(".dataId=").append(object.getID());
            hasWhere = true;
        }

        if (modelKey != null && !modelKey.isBlank()) {
            builder.append(hasWhere ? " AND " : " WHERE ");
            builder.append(alias).append(".modelKey='").append(modelKey).append("'");
        }

        return builder.toString();
    }

}
