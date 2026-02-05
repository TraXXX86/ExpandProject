package fr.expand.project.importdata.api;

import java.nio.file.Path;

import fr.expand.project.importdata.dto.DataPackObject;

public interface IImportAPI {

	/**
	 * Convert DataPack file to Java object
	 * 
	 * @param inputFile
	 * @return
	 */
	public DataPackObject readDataPack(Path inputFile);

	/**
	 * Import DataPack Java object to DB
	 * 
	 * @param inputDataPack
	 */
	public void importDataPack(DataPackObject inputDataPack);

}
