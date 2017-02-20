package fr.expand.project.importdata.api.impl;

import java.nio.file.Path;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import fr.expand.project.importdata.api.IImportAPI;
import fr.expand.project.importdata.dao.IConnectorDb;
import fr.expand.project.importdata.dao.connectors.impl.Neo4jConnector;
import fr.expand.project.importdata.dto.generated.DataPack;
import fr.expand.project.importdata.dto.generated.DataPackLink;
import fr.expand.project.importdata.dto.generated.DataPackObject;

public class ImportNeo4JAPI implements IImportAPI {

	@Override
	public DataPack readDataPack(Path inputFile) {
		try {
			JAXBContext jc = JAXBContext.newInstance("fr.expand.project.importdata.dto.generated");
			Unmarshaller u = jc.createUnmarshaller();
			return (DataPack) u.unmarshal(inputFile.toFile());
		} catch (JAXBException e) {
			e.printStackTrace();
		} finally {

		}
		return null;
	}

	@Override
	public void importDataPack(DataPack inputDataPack) {
		IConnectorDb connector = new Neo4jConnector();
		for(DataPackObject object : inputDataPack.getOBJECTS().getOBJECT()){
			connector.writeObject(object);
		}
		
		for(DataPackLink link : inputDataPack.getLINKS().getLINK()){
			//connector.writeLink(link.getOBJLINKA(), link.getOBJLINKB(), true);
		}

	}

}
