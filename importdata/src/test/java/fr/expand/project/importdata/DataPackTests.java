package fr.expand.project.importdata;

import fr.expand.project.importdata.dto.generated.ATTRIBUTE;
import fr.expand.project.importdata.dto.generated.DATAS;
import fr.expand.project.importdata.dto.generated.LINK;
import fr.expand.project.importdata.dto.generated.LINKS;
import fr.expand.project.importdata.dto.generated.OBJECT;
import fr.expand.project.importdata.dto.generated.OBJECTS;
import fr.expand.project.importdata.dto.generated.OBJLINK;
import fr.expand.project.importdata.xml.XmlSupport;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;

import org.junit.Assert;
import org.junit.Test;

import java.io.StringWriter;

public class DataPackTests {
    @Test
    public void marshalledDataRoundTripsThroughSchemaValidatedParser() throws Exception {
        DATAS source = new DATAS();
        source.setOBJECTS(new OBJECTS());
        source.setLINKS(new LINKS());

        OBJECT person = new OBJECT();
        person.setID(0);
        person.setTYPE("CUSTOM_PERSON");
        person.getATTRIBUTE().add(attribute("NAME", "Zoë & <friends>"));
        person.getATTRIBUTE().add(attribute("NOTE", ""));
        source.getOBJECTS().getOBJECT().add(person);

        OBJECT company = new OBJECT();
        company.setID(25);
        company.setTYPE("CUSTOM_COMPANY");
        company.getATTRIBUTE().add(attribute("NAME", "Expand"));
        source.getOBJECTS().getOBJECT().add(company);

        LINK link = new LINK();
        link.setTYPE("WORKS_FOR");
        link.setOBJLINKA(reference(person));
        link.setOBJLINKB(reference(company));
        link.getATTRIBUTE().add(attribute("ROLE", "Research & development"));
        source.getLINKS().getLINK().add(link);

        DATAS restored = XmlSupport.parseData(marshal(source));
        Assert.assertEquals(2, restored.getOBJECTS().getOBJECT().size());
        OBJECT restoredPerson = restored.getOBJECTS().getOBJECT().get(0);
        Assert.assertEquals(0, restoredPerson.getID());
        Assert.assertEquals("CUSTOM_PERSON", restoredPerson.getTYPE());
        Assert.assertEquals(2, restoredPerson.getATTRIBUTE().size());
        Assert.assertEquals("NAME", restoredPerson.getATTRIBUTE().get(0).getKEY());
        Assert.assertEquals("Zoë & <friends>", restoredPerson.getATTRIBUTE().get(0).getVALUE());
        Assert.assertEquals("", restoredPerson.getATTRIBUTE().get(1).getVALUE());
        Assert.assertEquals(25, restored.getOBJECTS().getOBJECT().get(1).getID());
        Assert.assertEquals(
                "Expand",
                restored.getOBJECTS().getOBJECT().get(1).getATTRIBUTE().get(0).getVALUE());

        Assert.assertEquals(1, restored.getLINKS().getLINK().size());
        LINK restoredLink = restored.getLINKS().getLINK().get(0);
        Assert.assertEquals("WORKS_FOR", restoredLink.getTYPE());
        Assert.assertEquals(0, restoredLink.getOBJLINKA().getID());
        Assert.assertEquals("CUSTOM_PERSON", restoredLink.getOBJLINKA().getTYPE());
        Assert.assertEquals(25, restoredLink.getOBJLINKB().getID());
        Assert.assertEquals("CUSTOM_COMPANY", restoredLink.getOBJLINKB().getTYPE());
        Assert.assertEquals("ROLE", restoredLink.getATTRIBUTE().get(0).getKEY());
        Assert.assertEquals(
                "Research & development", restoredLink.getATTRIBUTE().get(0).getVALUE());
    }

    @Test
    public void readsTypedEndpointFixtureAndDecodesEscapedAttributeValues() throws Exception {
        DATAS data =
                XmlSupport.parseData(
                        """
                        <DATAS>
                          <OBJECTS><OBJECT ID="7" TYPE="CUSTOM_PERSON">
                            <ATTRIBUTE KEY="NAME" VALUE="Alice &amp; Bob"/>
                          </OBJECT></OBJECTS>
                          <LINKS><LINK TYPE="KNOWS">
                            <ATTRIBUTE KEY="NOTE" VALUE="met in 2024"/>
                            <OBJ_LINK_A ID="7" TYPE="CUSTOM_PERSON"/>
                            <OBJ_LINK_B ID="7" TYPE="CUSTOM_PERSON"/>
                          </LINK></LINKS>
                        </DATAS>
                        """);
        Assert.assertEquals(7, data.getOBJECTS().getOBJECT().get(0).getID());
        Assert.assertEquals(
                "Alice & Bob",
                data.getOBJECTS().getOBJECT().get(0).getATTRIBUTE().get(0).getVALUE());
        LINK link = data.getLINKS().getLINK().get(0);
        Assert.assertEquals("KNOWS", link.getTYPE());
        Assert.assertEquals("CUSTOM_PERSON", link.getOBJLINKA().getTYPE());
        Assert.assertEquals(7, link.getOBJLINKB().getID());
        Assert.assertEquals("met in 2024", link.getATTRIBUTE().get(0).getVALUE());
    }

    @Test
    public void schemaInvalidDtoCannotRoundTripSilently() throws Exception {
        DATAS incomplete = new DATAS();
        incomplete.setOBJECTS(new OBJECTS());
        incomplete.setLINKS(new LINKS());
        OBJECT object = new OBJECT();
        object.setID(3);
        // TYPE is required by the schema, even though JAXB permits building this DTO.
        incomplete.getOBJECTS().getOBJECT().add(object);
        String invalidXml = marshal(incomplete);
        JAXBException failure =
                Assert.assertThrows(JAXBException.class, () -> XmlSupport.parseData(invalidXml));
        Assert.assertNotNull(failure.getMessage());
    }

    @Test
    public void rejectsOutOfRangeIdsAndIncompleteLinkEndpoints() {
        Assert.assertThrows(
                JAXBException.class,
                () ->
                        XmlSupport.parseData(
                                """
<DATAS><OBJECTS><OBJECT ID="2147483648" TYPE="CUSTOM_PERSON"/></OBJECTS><LINKS/></DATAS>
"""));
        Assert.assertThrows(
                JAXBException.class,
                () ->
                        XmlSupport.parseData(
                                """
                                <DATAS><OBJECTS/><LINKS><LINK TYPE="KNOWS">
                                  <OBJ_LINK_A ID="1" TYPE="CUSTOM_PERSON"/>
                                </LINK></LINKS></DATAS>
                                """));
    }

    private static ATTRIBUTE attribute(String key, String value) {
        ATTRIBUTE attribute = new ATTRIBUTE();
        attribute.setKEY(key);
        attribute.setVALUE(value);
        return attribute;
    }

    private static OBJLINK reference(OBJECT object) {
        OBJLINK reference = new OBJLINK();
        reference.setID(object.getID());
        reference.setTYPE(object.getTYPE());
        return reference;
    }

    private static String marshal(DATAS data) throws JAXBException {
        Marshaller marshaller = JAXBContext.newInstance(DATAS.class).createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        StringWriter output = new StringWriter();
        marshaller.marshal(data, output);
        return output.toString();
    }
}
