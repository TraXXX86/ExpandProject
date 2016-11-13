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
import fr.expand.project.importdata.dto.DataPackObject;
import fr.expand.project.importdata.util.CypherUtils;

public class Neo4jConnector extends IConnectorDb {

	private Connection conn = null;

	// ############################# Start/Close Connection to DB methods

	@Override
	protected void connectToDb() {
		if (conn == null) {
			try {
				conn = DriverManager.getConnection("jdbc:neo4j:http://localhost:7474", "neo4j", "expand");
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
		String request = "CREATE (a:" + CypherUtils.convertObjectForDbSubtitution(object) + ") RETURN ID(a) AS ID";
		System.out.println(request);

		// Create parameters
		Map<String, Object> params = new HashMap<>();
		int i = 1;
		for (Entry<String, Object> attribute : object.getAttributes().entrySet()) {
			params.put(Integer.toString(i), attribute.getValue());
			i++;
		}

		// Launch request
		List<DataPackObject> results = query(request, params);

		if (!CollectionUtils.isEmpty(results)) {
			return results.get(0).getId();
		}
		return -1;
	}

	@Override
	public int writeLink(DataPackObject objectA, DataPackObject objectB, boolean isOriented) {
		connectToDb();

		// Create query
		String request = "MATCH (a:" + objectA.getType().toString() + ") WHERE ID(a)={1} " + "MATCH (b:"
				+ objectB.getType().toString() + ") WHERE ID(b)={2} " + "CREATE (a)-[:KNOWS]->(b)";
		System.out.println(request);

		// Create parameters
		Map<String, Object> params = new HashMap<>();
		params.put("1", objectA.getId());
		params.put("2", objectB.getId());

		// Launch request
		List<DataPackObject> results = query(request, params);

		if (!CollectionUtils.isEmpty(results)) {
			return results.get(0).getId();
		}
		return -1;
	}

	@Override
	public DataPackObject getObjectToDbDto(ObjectTypeEnum typeObject, int idObject) {
		connectToDb();

		// Create query
		String request = "MATCH (n:HUMAIN) WHERE ID(n)={1} RETURN n, ID(n) AS ID LIMIT 5";
		System.out.println(request);

		// Create parameters
		Map<String, Object> params = new HashMap<>();
		params.put("1", idObject);

		// Launch request
		List<DataPackObject> results = query(request, params);

		if (!CollectionUtils.isEmpty(results)) {
			return results.get(0);
		}
		return null;
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
			final ResultSet result = statement.executeQuery();

			List<String> columnsList = getColumns(result);
			while (result.next()) {
				DataPackObject rowObject = new DataPackObject(ObjectTypeEnum.HUMAIN);
				for (String column : columnsList) {
					if (StringUtils.equals(column, "ID")) {
						rowObject.setId(result.getInt(column));
					} else {
						Map<String, Object> row = (Map<String, Object>) result.getObject(column);
						rowObject.getAttributes().putAll(row);
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

}
