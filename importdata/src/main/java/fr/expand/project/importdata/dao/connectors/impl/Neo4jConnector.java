package fr.expand.project.importdata.dao.connectors.impl;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import fr.expand.project.commons.ObjectTypeEnum;
import fr.expand.project.importdata.dao.IConnectorDb;
import fr.expand.project.importdata.dto.generated.ATTRIBUTE;
import fr.expand.project.importdata.dto.DataPackAttribute;
import fr.expand.project.importdata.dto.DataPackObject;
import fr.expand.project.importdata.util.CypherUtils;

public class Neo4jConnector extends IConnectorDb {

	private Connection conn = null;
	private static final String DEFAULT_HTTP_URI = "jdbc:neo4j:http://localhost:7474";
	private static final String DEFAULT_USER = "neo4j";
	private static final String DEFAULT_PASSWORD = "expand";

	// ############################# Start/Close Connection to DB methods

	@Override
	protected void connectToDb() {
		if (conn == null) {
			try {
				String uri = readSetting("NEO4J_HTTP_URI", "NEO4J_JDBC_URI", DEFAULT_HTTP_URI);
				String auth = readSetting("NEO4J_AUTH", null, null);
				if (auth != null && !auth.isBlank()) {
					if ("none".equalsIgnoreCase(auth.trim())) {
						conn = DriverManager.getConnection(uri);
					} else {
						int separatorIndex = auth.indexOf('/');
						if (separatorIndex > 0 && separatorIndex < auth.length() - 1) {
							String user = auth.substring(0, separatorIndex);
							String password = auth.substring(separatorIndex + 1);
							conn = DriverManager.getConnection(uri, user, password);
						}
					}
				}
				if (conn == null) {
					String user = readSetting("NEO4J_USER", null, DEFAULT_USER);
					String password = readSetting("NEO4J_PASSWORD", null, DEFAULT_PASSWORD);
					conn = DriverManager.getConnection(uri, user, password);
				}
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	protected void closeConnection() {
		if (conn != null) {
			try {
				if (!conn.isClosed()) {
					conn.close();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	// ############################# Request methods

	@Override
	public int writeObject(DataPackObject object) {
		connectToDb();

		// Create query
		String request = "CREATE (a:" + CypherUtils.convertObjectForDbSubtitution(object, modelKey)
				+ ") RETURN ID(a) AS ID";
		LOGGER.info(request);

		// Create parameters
		Map<String, Object> params = new HashMap<>();
		int i = 1;
		for (ATTRIBUTE attribute : object.getATTRIBUTE()) {
			params.put(Integer.toString(i), attribute.getVALUE());
			i++;
		}
		if (modelKey != null && !modelKey.isBlank()) {
			params.put(Integer.toString(i), modelKey);
		}

		// Launch request
		List<DataPackObject> results = query(request, params);

		if (!CollectionUtils.isEmpty(results)) {
			object.setID(results.get(0).getID());
			return results.get(0).getID();
		}
		return -1;
	}

	@Override
	public int writeLink(DataPackObject objectA, DataPackObject objectB, boolean isOriented) {
		connectToDb();

		// Create query
		StringBuilder request = new StringBuilder();
		request.append("MATCH (a:").append(objectA.getTYPE()).append(") WHERE ID(a)=?");
		if (modelKey != null && !modelKey.isBlank()) {
			request.append(" AND a.modelKey=?");
		}
		request.append(" MATCH (b:").append(objectB.getTYPE()).append(") WHERE ID(b)=?");
		if (modelKey != null && !modelKey.isBlank()) {
			request.append(" AND b.modelKey=?");
		}
		request.append(" CREATE (a)-[:KNOWS");
		if (modelKey != null && !modelKey.isBlank()) {
			request.append(" {modelKey:?}");
		}
		request.append("]->(b)");
		String requestString = request.toString();
		LOGGER.info(requestString);

		// Create parameters
		Map<String, Object> params = new HashMap<>();
		int index = 1;
		params.put(Integer.toString(index++), objectA.getID());
		if (modelKey != null && !modelKey.isBlank()) {
			params.put(Integer.toString(index++), modelKey);
		}
		params.put(Integer.toString(index++), objectB.getID());
		if (modelKey != null && !modelKey.isBlank()) {
			params.put(Integer.toString(index++), modelKey);
		}
		if (modelKey != null && !modelKey.isBlank()) {
			params.put(Integer.toString(index), modelKey);
		}

		// Launch request
		List<DataPackObject> results = query(requestString, params);

		if (!CollectionUtils.isEmpty(results)) {
			return results.get(0).getID();
		}
		return -1;
	}

	@Override
	public DataPackObject getObjectToDbDto(ObjectTypeEnum typeObject, int idObject) {
		connectToDb();

		// Create query
		StringBuilder request = new StringBuilder();
		request.append("MATCH (n:").append(typeObject.name()).append(") WHERE ID(n)=?");
		if (modelKey != null && !modelKey.isBlank()) {
			request.append(" AND n.modelKey=?");
		}
		request.append(" RETURN n, ID(n) AS ID LIMIT 5");
		String requestString = request.toString();
		LOGGER.info(requestString);

		// Create parameters
		Map<String, Object> params = new HashMap<>();
		params.put("1", idObject);
		if (modelKey != null && !modelKey.isBlank()) {
			params.put("2", modelKey);
		}

		// Launch request
		List<DataPackObject> results = query(requestString, params);

		if (!CollectionUtils.isEmpty(results)) {
			return results.get(0);
		}
		return null;
	}

	@Override
	public void deleteAll() {
		closeConnection();
		connectToDb();

		// Create query
		String request = "MATCH (n) DETACH DELETE n";
		LOGGER.info(request);

		// Launch request
		query(request, new HashMap<>());
	}

	// ############################# Utils methods

	/**
	 * Get columns keys list
	 * 
	 * @param result
	 * @return
	 * @throws SQLException
	 */
	private List<String> getColumns(ResultSet result) throws SQLException {
		ResultSetMetaData metaData = result.getMetaData();
		int count = metaData.getColumnCount();
		List<String> cols = new ArrayList<>(count);
		for (int i = 1; i <= count; i++) {
			cols.add(metaData.getColumnName(i));
		}
		return cols;
	}

	/**
	 * Launch Query and get result list
	 * 
	 * @param query
	 * @param params
	 * @return
	 */
	private List<DataPackObject> query(String query, Map<String, Object> params) {
		List<DataPackObject> results = new ArrayList<>();
		try {
			final PreparedStatement statement = conn.prepareStatement(query);
			setParameters(statement, params);
			boolean hasResultSet = statement.execute();
			if (!hasResultSet) {
				return results;
			}
			final ResultSet result = statement.getResultSet();
			if (result == null) {
				return results;
			}

			List<String> columnsList = getColumns(result);
			while (result.next()) {
				DataPackObject rowObject = new DataPackObject();
				rowObject.setTYPE(ObjectTypeEnum.HUMAIN.toString());
				for (String column : columnsList) {
					if (StringUtils.equals(column, "ID")) {
						rowObject.setID(result.getInt(column));
					} else {
						@SuppressWarnings("unchecked")
						Map<String, Object> row = (Map<String, Object>) result.getObject(column);

						for (Entry<String, Object> content : row.entrySet()) {
							if (content.getValue() instanceof String) {
								DataPackAttribute attribute = new DataPackAttribute(content.getKey(),
										(String) content.getValue());
								rowObject.getATTRIBUTE().add(attribute);
							}
						}
					}
				}
				results.add(rowObject);
			}

		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return results;
	}

	private void setParameters(PreparedStatement statement, Map<String, Object> params) throws SQLException {
		for (Map.Entry<String, Object> entry : params.entrySet()) {
			int index = Integer.parseInt(entry.getKey());
			statement.setObject(index, entry.getValue());
		}
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
