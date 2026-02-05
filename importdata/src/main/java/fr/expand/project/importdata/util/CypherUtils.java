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
		StringBuilder result = new StringBuilder();
		result.append(object.getTYPE()).append(IConstantUtils.SPACE);
		if (!object.getATTRIBUTE().isEmpty()) {
			boolean isFirst = true;
			result.append("{");
			for (ATTRIBUTE attribute : object.getATTRIBUTE()) {
				if (!isFirst) {
					result.append(",");
				} else {
					isFirst = false;
				}
				result.append(attribute.getKEY()).append(":'").append(attribute.getVALUE()).append("'");
			}
			result.append("}");
		}
		return result.toString();
	}

	public static String convertObjectForDbSubtitution(DataPackObject object) {
		StringBuilder result = new StringBuilder();
		result.append(object.getTYPE()).append(IConstantUtils.SPACE);
		if (!object.getATTRIBUTE().isEmpty()) {
			boolean isFirst = true;
			result.append("{");
			for (ATTRIBUTE attribute : object.getATTRIBUTE()) {
				if (!isFirst) {
					result.append(",");
				} else {
					isFirst = false;
				}
				result.append(attribute.getKEY()).append(":?");
			}
			result.append("}");
		}
		return result.toString();
	}

}
