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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import fr.expand.project.commons.ObjectTypeEnum;
import fr.expand.project.importdata.dao.IConnectorDb;
import fr.expand.project.importdata.dto.ObjectToDbDto;

public class Neo4jConnector implements IConnectorDb {

	private Connection conn = null;

	public Neo4jConnector() {
		super();
		try {
			conn = DriverManager.getConnection("jdbc:neo4j:http://localhost:7474", "neo4j", "expand");
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void finalize() throws Throwable {
		try {
			if (conn != null && !conn.isClosed()) {
				conn.close();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		super.finalize();
	}

	@Override
	public int writeObject(ObjectToDbDto object) {
		return -1;
	}

	@Override
	public int writeLink(ObjectToDbDto objectA, ObjectToDbDto objectB, boolean isOriented) {
		return 0;
	}

	@Override
	public ObjectToDbDto getObjectToDbDto(ObjectTypeEnum typeObject, int idObject) {

		String request = "MATCH (n:HUMAIN) WHERE ID(n)={1} RETURN n, ID(n) AS ID LIMIT 5";
		System.out.println(request);

		Map<String, Object> params = new HashMap<>();
		params.put("1", idObject);
		List<ObjectToDbDto> results = query(request, params);

		if (!CollectionUtils.isEmpty(results)) {
			return results.get(0);
		}
		return null;
	}

	private List<String> getColumns(ResultSet result) throws SQLException {
		ResultSetMetaData metaData = result.getMetaData();
		int count = metaData.getColumnCount();
		List<String> cols = new ArrayList<>(count);
		for (int i = 1; i <= count; i++) {
			cols.add(metaData.getColumnName(i));
		}
		return cols;
	}

	private List<ObjectToDbDto> query(String query, Map<String, Object> params) {
		List<ObjectToDbDto> results = new ArrayList<>();
		try {
			final PreparedStatement statement = conn.prepareStatement(query);
			setParameters(statement, params);
			final ResultSet result = statement.executeQuery();

			List<String> columnsList = getColumns(result);
			while (result.next()) {
				ObjectToDbDto rowObject = new ObjectToDbDto(ObjectTypeEnum.HUMAIN);
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
