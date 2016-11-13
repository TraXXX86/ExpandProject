package fr.expand.project.importdata.util;

import fr.expand.project.commons.IConstantUtils;
import fr.expand.project.importdata.dto.generated.DataPackAttribute;
import fr.expand.project.importdata.dto.generated.DataPackObject;

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
		if (!object.getATTRIBUTES().isEmpty()) {
			boolean isFirst = true;
			result.append("{");
			for (DataPackAttribute attribute : object.getATTRIBUTES()) {
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
		if (!object.getATTRIBUTES().isEmpty()) {
			boolean isFirst = true;
			result.append("{");
			int i = 1;
			for (DataPackAttribute attribute : object.getATTRIBUTES()) {
				if (!isFirst) {
					result.append(",");
				} else {
					isFirst = false;
				}
				result.append(attribute.getKEY()).append(":{").append(i).append("}");
				i++;
			}
			result.append("}");
		}
		return result.toString();
	}

}
