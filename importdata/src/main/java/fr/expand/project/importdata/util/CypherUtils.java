package fr.expand.project.importdata.util;

import java.util.Map.Entry;

import fr.expand.project.commons.IConstantUtils;
import fr.expand.project.importdata.dto.ObjectToDbDto;

public class CypherUtils {

	/**
	 * Convert Object to string representation use by Cypher request
	 * 
	 * @param object
	 * @return
	 */
	public static String convertObjectForDb(ObjectToDbDto object) {
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

	public static String convertObjectForDbSubtitution(ObjectToDbDto object) {
		StringBuilder result = new StringBuilder();
		result.append(object.getType()).append(IConstantUtils.SPACE);
		if (!object.getAttributes().isEmpty()) {
			boolean isFirst = true;
			result.append("{");
			int i = 1;
			for (Entry<String, Object> entry : object.getAttributes().entrySet()) {
				if (!isFirst) {
					result.append(",");
				} else {
					isFirst = false;
				}
				result.append(entry.getKey()).append(":{").append(i).append("}");
				i++;
			}
			result.append("}");
		}
		return result.toString();
	}

}
