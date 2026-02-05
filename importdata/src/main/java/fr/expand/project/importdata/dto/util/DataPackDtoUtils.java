package fr.expand.project.importdata.dto.util;

import fr.expand.project.importdata.dto.DataPackObject;
import fr.expand.project.importdata.dto.DataPackObjectLink;

public class DataPackDtoUtils {
	
	public static DataPackObjectLink createObjLink(DataPackObject object){
		return new DataPackObjectLink(object.getID(), object.getTYPE());
	}
	
}
