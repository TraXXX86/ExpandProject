package fr.expand.project.importdata;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.junit.Test;

import fr.expand.project.commons.ObjectTypeEnum;
import fr.expand.project.importdata.dto.generated.DataPack;
import fr.expand.project.importdata.dto.generated.DataPackObject;
import fr.expand.project.importdata.dto.generated.DataPackObjects;

public class DataPackTests {

	@Test
	public void test() {
		DataPack datapack = new DataPack();
		datapack.setOBJECTS(new DataPackObjects());

		DataPackObject object = new DataPackObject();
		object.setTYPE(ObjectTypeEnum.HUMAIN.toString());
		datapack.getOBJECTS().getOBJECT().add(object);

		DataPackObject object2 = new DataPackObject();
		object2.setTYPE(ObjectTypeEnum.HUMAIN.toString());
		datapack.getOBJECTS().getOBJECT().add(object2);

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
