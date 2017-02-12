package fr.expand.project.importdata;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

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

	@Test
	public void test_createXML() {
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

		createXML(datapack);
	}

	private void createXML(DataPack datapack) {
		try {
			JAXBContext jaxbContext = JAXBContext.newInstance("fr.expand.project.importdata.dto.generated");
			Marshaller marshaller = jaxbContext.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, new Boolean(true));
			marshaller.marshal(datapack, System.out);
		} catch (JAXBException e) {
			e.printStackTrace();
		} finally {

		}
	}
	
	@Test
	public void test_readXML() {
		
	}

	private void readXML() {

		try {
			JAXBContext jc = JAXBContext.newInstance("fr.expand.project.importdata.dto.generated");
			Unmarshaller u = jc.createUnmarshaller();
		} catch (JAXBException e) {
			e.printStackTrace();
		} finally {

		}
	}

}
