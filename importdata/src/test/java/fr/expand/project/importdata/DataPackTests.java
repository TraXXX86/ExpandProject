package fr.expand.project.importdata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import fr.expand.project.commons.LinkTypeEnum;
import fr.expand.project.commons.ObjectTypeEnum;
import fr.expand.project.importdata.dto.generated.DataPack;
import fr.expand.project.importdata.dto.generated.DataPackAttribute;
import fr.expand.project.importdata.dto.generated.DataPackLink;
import fr.expand.project.importdata.dto.generated.DataPackLinks;
import fr.expand.project.importdata.dto.generated.DataPackObject;
import fr.expand.project.importdata.dto.generated.DataPackObjects;
import fr.expand.project.importdata.dto.util.DataPackDtoUtils;

public class DataPackTests {
	
	protected static final Logger LOGGER = LogManager.getLogger(DataPackTests.class.toString());

	@Test
	public void test_createXML() {
		LOGGER.info("######## test_createXML");
		
		DataPack datapack = new DataPack();
		datapack.setOBJECTS(new DataPackObjects());
		datapack.setLINKS(new DataPackLinks());

		DataPackObject object = new DataPackObject();
		object.setID(1);
		object.setTYPE(ObjectTypeEnum.HUMAIN.toString());
		datapack.getOBJECTS().getOBJECT().add(object);
		
		object.getATTRIBUTE().add(new DataPackAttribute("ATTR_1", "Valeur 1"));
		object.getATTRIBUTE().add(new DataPackAttribute("ATTR_2", "Valeur 2"));

		DataPackObject object2 = new DataPackObject();
		object2.setID(2);
		object2.setTYPE(ObjectTypeEnum.HUMAIN.toString());
		datapack.getOBJECTS().getOBJECT().add(object2);
		
		DataPackLink link = new DataPackLink();
		link.setOBJLINKA(DataPackDtoUtils.createObjLink(object));
		link.setOBJLINKB(DataPackDtoUtils.createObjLink(object2));
		link.setTYPE(LinkTypeEnum.CONNAISSANCE.toString());		
		
		link.getATTRIBUTE().add(new DataPackAttribute("ATTR_1", "Valeur 1"));
		link.getATTRIBUTE().add(new DataPackAttribute("ATTR_2", "Valeur 2"));
		
		datapack.getLINKS().getLINK().add(link);

		createXML(datapack, "test.xml");
	}

	private void createXML(DataPack datapack, String fileName) {
		try {
			JAXBContext jaxbContext = JAXBContext.newInstance("fr.expand.project.importdata.dto.generated");
			Marshaller marshaller = jaxbContext.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, new Boolean(true));
			
			// Get output file path
			Path tmpFolder = Paths.get("tmp");
			if(!Files.exists(tmpFolder)){
				Files.createDirectory(Paths.get("tmp"));
			}
			Path tmpFilePath = tmpFolder.resolve(fileName);  
	
			// Create file content
			marshaller.marshal(datapack, tmpFilePath.toFile());
		} catch (JAXBException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {

		}
	}
	
	@Test
	public void test_readXML() {
		LOGGER.info("######## test_readXML");
		DataPack datapack  = readXML("test.xml");
		createXML(datapack, "outputfile.xml");
	}

	private DataPack readXML(String fileName) {
		try {
			JAXBContext jc = JAXBContext.newInstance("fr.expand.project.importdata.dto.generated");
			Unmarshaller u = jc.createUnmarshaller();
			
			// Get output file path
			Path tmpFolder = Paths.get("tmp");
			if(!Files.exists(tmpFolder)){
				Files.createDirectory(Paths.get("tmp"));
			}
			Path tmpFilePath = tmpFolder.resolve(fileName);  
			
			return (DataPack) u.unmarshal(tmpFilePath.toFile());
		} catch (JAXBException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {

		}
		return null;
	}

}
