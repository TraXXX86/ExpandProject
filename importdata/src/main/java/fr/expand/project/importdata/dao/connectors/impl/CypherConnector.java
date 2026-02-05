package fr.expand.project.importdata.dao.connectors.impl;

import java.util.Map.Entry;

import org.neo4j.driver.AuthToken;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;

import fr.expand.project.commons.ObjectTypeEnum;
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

	private Driver driver;
	private Session session;
	private static final String DEFAULT_BOLT_URI = "bolt://localhost:7687";
	private static final String DEFAULT_USER = "neo4j";
	private static final String DEFAULT_PASSWORD = "expand";

	// ############################# Start/Close Connection to DB methods

	@Override
	protected void connectToDb() {
		String uri = readSetting("NEO4J_BOLT_URI", "NEO4J_URI", DEFAULT_BOLT_URI);
		AuthToken authToken = buildAuthToken();
		driver = GraphDatabase.driver(uri, authToken);
		session = driver.session();
	}

	@Override
	public void closeConnection() {
		if (session != null) {
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

	private AuthToken buildAuthToken() {
		String auth = readSetting("NEO4J_AUTH", null, null);
		if (auth != null && !auth.isBlank()) {
			if ("none".equalsIgnoreCase(auth.trim())) {
				return AuthTokens.none();
			}
			int separatorIndex = auth.indexOf('/');
			if (separatorIndex > 0 && separatorIndex < auth.length() - 1) {
				String user = auth.substring(0, separatorIndex);
				String password = auth.substring(separatorIndex + 1);
				return AuthTokens.basic(user, password);
			}
		}

		String user = readSetting("NEO4J_USER", null, DEFAULT_USER);
		String password = readSetting("NEO4J_PASSWORD", null, DEFAULT_PASSWORD);
		return AuthTokens.basic(user, password);
	}

	private String readSetting(String envKey, String fallbackEnvKey, String defaultValue) {
		String value = System.getProperty(envKey);
		if (value == null || value.isBlank()) {
			value = System.getenv(envKey);
		}
		if ((value == null || value.isBlank()) && fallbackEnvKey != null) {
			value = System.getProperty(fallbackEnvKey);
			if (value == null || value.isBlank()) {
				value = System.getenv(fallbackEnvKey);
			}
		}
		return (value == null || value.isBlank()) ? defaultValue : value;
	}

}
