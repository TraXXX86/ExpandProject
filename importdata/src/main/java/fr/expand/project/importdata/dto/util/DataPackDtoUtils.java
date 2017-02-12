package fr.expand.project.importdata.dto.util;

import fr.expand.project.importdata.dto.generated.DataPackObject;
import fr.expand.project.importdata.dto.generated.DataPackObjectLink;

public class DataPackDtoUtils {
	
	public static DataPackObjectLink createObjLink(DataPackObject object){
		return new DataPackObjectLink(object.getID(), object.getTYPE());
	}
	
}
