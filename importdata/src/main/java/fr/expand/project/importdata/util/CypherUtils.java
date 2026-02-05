package fr.expand.project.importdata.util;

import fr.expand.project.commons.IConstantUtils;
import fr.expand.project.importdata.dto.generated.ATTRIBUTE;
import fr.expand.project.importdata.dto.DataPackAttribute;
import fr.expand.project.importdata.dto.DataPackObject;

public class CypherUtils {

	/**
	 * Convert Object to string representation use by Cypher request
	 * 
	 * @param object
	 * @return
	 */
	public static String convertObjectForDb(DataPackObject object) {
		return convertObjectForDb(object, null);
	}

	public static String convertObjectForDb(DataPackObject object, String modelKey) {
		StringBuilder result = new StringBuilder();
		result.append(object.getTYPE());
		if (modelKey != null && !modelKey.isBlank()) {
			result.append(":DataObject");
		}
		String properties = buildPropertiesLiteral(object, modelKey, false);
		if (!properties.isEmpty()) {
			result.append(IConstantUtils.SPACE).append(properties);
		}
		return result.toString();
	}

	public static String convertObjectForDbSubtitution(DataPackObject object) {
		return convertObjectForDbSubtitution(object, null);
	}

	public static String convertObjectForDbSubtitution(DataPackObject object, String modelKey) {
		StringBuilder result = new StringBuilder();
		result.append(object.getTYPE());
		if (modelKey != null && !modelKey.isBlank()) {
			result.append(":DataObject");
		}
		String properties = buildPropertiesLiteral(object, modelKey, true);
		if (!properties.isEmpty()) {
			result.append(IConstantUtils.SPACE).append(properties);
		}
		return result.toString();
	}

	private static String buildPropertiesLiteral(DataPackObject object, String modelKey, boolean parameterized) {
		boolean hasAttributes = !object.getATTRIBUTE().isEmpty();
		boolean hasModelKey = modelKey != null && !modelKey.isBlank();
		if (!hasAttributes && !hasModelKey) {
			return "";
		}

		StringBuilder result = new StringBuilder();
		result.append("{");
		boolean isFirst = true;
		for (ATTRIBUTE attribute : object.getATTRIBUTE()) {
			if (!isFirst) {
				result.append(",");
			} else {
				isFirst = false;
			}
			result.append(attribute.getKEY()).append(":");
			if (parameterized) {
				result.append("?");
			} else {
				result.append("'").append(attribute.getVALUE()).append("'");
			}
		}
		if (hasModelKey) {
			if (!isFirst) {
				result.append(",");
			}
			result.append("modelKey").append(":");
			if (parameterized) {
				result.append("?");
			} else {
				result.append("'").append(modelKey).append("'");
			}
		}
		result.append("}");
		return result.toString();
	}

}
