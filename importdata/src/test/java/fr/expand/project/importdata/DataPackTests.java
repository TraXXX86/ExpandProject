package fr.expand.project.importdata;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;

import org.junit.Test;

import fr.expand.project.commons.LinkTypeEnum;
import fr.expand.project.commons.ObjectTypeEnum;
import fr.expand.project.importdata.dto.generated.DATAS;
import fr.expand.project.importdata.dto.generated.LINK;
import fr.expand.project.importdata.dto.generated.LINKS;
import fr.expand.project.importdata.dto.generated.OBJECT;
import fr.expand.project.importdata.dto.generated.OBJECTS;
import fr.expand.project.importdata.dto.generated.DataPackAttribute;
import fr.expand.project.importdata.dto.generated.DataPackObject;
import fr.expand.project.importdata.dto.util.DataPackDtoUtils;

public class DataPackTests {

	@Test
	public void test_createXML() {
		DATAS datapack = new DATAS();
		datapack.setOBJECTS(new OBJECTS());
		datapack.setLINKS(new LINKS());

		OBJECT object = new OBJECT();
		object.setID(1);
		object.setTYPE(ObjectTypeEnum.HUMAIN.toString());
		datapack.getOBJECTS().getOBJECT().add(object);
		
		object.getATTRIBUTE().add(new DataPackAttribute("ATTR_1", "Valeur 1"));
		object.getATTRIBUTE().add(new DataPackAttribute("ATTR_2", "Valeur 2"));

		OBJECT object2 = new OBJECT();
		object2.setID(2);
		object2.setTYPE(ObjectTypeEnum.HUMAIN.toString());
		datapack.getOBJECTS().getOBJECT().add(object2);
		
		// Create wrapper objects for link
		DataPackObject dpObj1 = new DataPackObject();
		dpObj1.setID(object.getID());
		dpObj1.setTYPE(object.getTYPE());
		
		DataPackObject dpObj2 = new DataPackObject();
		dpObj2.setID(object2.getID());
		dpObj2.setTYPE(object2.getTYPE());
		
		LINK link = new LINK();
		link.setOBJLINKA(DataPackDtoUtils.createObjLink(dpObj1));
		link.setOBJLINKB(DataPackDtoUtils.createObjLink(dpObj2));
		link.setTYPE(LinkTypeEnum.CONNAISSANCE.toString());		
		
		link.getATTRIBUTE().add(new DataPackAttribute("ATTR_1", "Valeur 1"));
		link.getATTRIBUTE().add(new DataPackAttribute("ATTR_2", "Valeur 2"));
		
		datapack.getLINKS().getLINK().add(link);

		createXML(datapack);
	}

	private void createXML(DATAS datapack) {
		try {
			JAXBContext jaxbContext = JAXBContext.newInstance(DATAS.class);
			Marshaller marshaller = jaxbContext.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			marshaller.marshal(datapack, System.out);
		} catch (JAXBException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void test_readXML() {
		
	}

	private void readXML() {
		try {
			JAXBContext jc = JAXBContext.newInstance(DATAS.class);
			Unmarshaller u = jc.createUnmarshaller();
			// Add actual file reading logic here when needed
		} catch (JAXBException e) {
			e.printStackTrace();
		}
	}

}
