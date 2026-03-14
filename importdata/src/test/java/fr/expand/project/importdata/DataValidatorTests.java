package fr.expand.project.importdata;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import fr.expand.project.importdata.dto.DataPackAttribute;
import fr.expand.project.importdata.dto.DataPackObject;
import fr.expand.project.importdata.dto.generated.DATAS;
import fr.expand.project.importdata.dto.generated.LINK;
import fr.expand.project.importdata.dto.generated.LINKS;
import fr.expand.project.importdata.dto.generated.OBJECT;
import fr.expand.project.importdata.dto.generated.OBJECTS;
import fr.expand.project.importdata.dto.util.DataPackDtoUtils;
import fr.expand.project.importdata.model.ModelManager;
import fr.expand.project.importdata.validation.DataValidator;
import fr.expand.project.importdata.validation.ValidationError;
import fr.expand.project.importdata.validation.ValidationResult;

public class DataValidatorTests {

    private static final String VALIDATION_MODEL_XML = """
        <DATA_MODEL NAME="validation-model" VERSION="1.0">
            <OBJECT_TYPES>
                <OBJECT_TYPE NAME="PERSONNE">
                    <ATTRIBUTE_DEFINITIONS>
                        <ATTRIBUTE_DEFINITION NAME="NOM" TYPE="STRING" REQUIRED="true"/>
                        <ATTRIBUTE_DEFINITION NAME="PRENOM" TYPE="STRING" REQUIRED="true"/>
                        <ATTRIBUTE_DEFINITION NAME="AGE" TYPE="INTEGER" REQUIRED="false"/>
                    </ATTRIBUTE_DEFINITIONS>
                </OBJECT_TYPE>
                <OBJECT_TYPE NAME="ENTREPRISE">
                    <ATTRIBUTE_DEFINITIONS>
                        <ATTRIBUTE_DEFINITION NAME="NOM" TYPE="STRING" REQUIRED="true"/>
                    </ATTRIBUTE_DEFINITIONS>
                </OBJECT_TYPE>
            </OBJECT_TYPES>
            <LINK_TYPES>
                <LINK_TYPE NAME="CONNAIT" DIRECTED="false">
                    <SOURCE_TYPES>
                        <TYPE_REF NAME="PERSONNE"/>
                    </SOURCE_TYPES>
                    <TARGET_TYPES>
                        <TYPE_REF NAME="PERSONNE"/>
                    </TARGET_TYPES>
                    <ATTRIBUTE_DEFINITIONS>
                        <ATTRIBUTE_DEFINITION NAME="DEPUIS" TYPE="DATE" REQUIRED="true"/>
                        <ATTRIBUTE_DEFINITION NAME="TYPE_RELATION" TYPE="STRING" REQUIRED="false" DEFAULT_VALUE="COLLEGUE"/>
                    </ATTRIBUTE_DEFINITIONS>
                </LINK_TYPE>
                <LINK_TYPE NAME="TRAVAILLE_POUR" DIRECTED="true">
                    <SOURCE_TYPES>
                        <TYPE_REF NAME="PERSONNE"/>
                    </SOURCE_TYPES>
                    <TARGET_TYPES>
                        <TYPE_REF NAME="ENTREPRISE"/>
                    </TARGET_TYPES>
                    <ATTRIBUTE_DEFINITIONS>
                        <ATTRIBUTE_DEFINITION NAME="DATE_DEBUT" TYPE="DATE" REQUIRED="false"/>
                    </ATTRIBUTE_DEFINITIONS>
                </LINK_TYPE>
            </LINK_TYPES>
        </DATA_MODEL>
        """;

    @Before
    public void setupModel() throws Exception {
        ModelManager manager = ModelManager.getInstance();
        manager.clearModels();
        manager.loadModelFromXml(VALIDATION_MODEL_XML);
    }

    @Test
    public void validate_shouldDetectMissingRequiredAttribute() {
        DATAS data = new DATAS();
        data.setOBJECTS(new OBJECTS());

        data.getOBJECTS().getOBJECT().add(createPerson(1, "Dupont", null, null));

        ValidationResult result = new DataValidator().validate(data);

        Assert.assertFalse(result.isValid());
        Assert.assertTrue(containsErrorMessage(result, "Missing required attribute: PRENOM"));
    }

    @Test
    public void validate_shouldDetectInvalidAttributeType() {
        DATAS data = new DATAS();
        data.setOBJECTS(new OBJECTS());

        data.getOBJECTS().getOBJECT().add(createPerson(2, "Martin", "Alice", "vingt-cinq"));

        ValidationResult result = new DataValidator().validate(data);

        Assert.assertFalse(result.isValid());
        Assert.assertTrue(containsErrorMessage(result, "Invalid type for attribute 'AGE': expected INTEGER"));
    }

    @Test
    public void validate_shouldRequireLinkAttributesDuringImport() {
        DATAS data = new DATAS();
        data.setOBJECTS(new OBJECTS());
        data.setLINKS(new LINKS());

        DataPackObject source = createPerson(1, "Dupont", "Alice", null);
        DataPackObject target = createPerson(2, "Martin", "Bob", null);
        data.getOBJECTS().getOBJECT().add(source);
        data.getOBJECTS().getOBJECT().add(target);

        LINK link = createLink(source, target, "CONNAIT");
        data.getLINKS().getLINK().add(link);

        ValidationResult result = new DataValidator().validate(data);

        Assert.assertFalse(result.isValid());
        Assert.assertTrue(containsErrorMessage(result, "Missing required attribute 'DEPUIS'"));
        Assert.assertTrue(containsErrorMessage(result, "LINK[1 CONNAIT 1:PERSONNE -> 2:PERSONNE]"));
    }

    @Test
    public void validate_shouldApplyLinkDefaultsDuringImport() {
        DATAS data = new DATAS();
        data.setOBJECTS(new OBJECTS());
        data.setLINKS(new LINKS());

        DataPackObject source = createPerson(1, "Dupont", "Alice", null);
        DataPackObject target = createPerson(2, "Martin", "Bob", null);
        data.getOBJECTS().getOBJECT().add(source);
        data.getOBJECTS().getOBJECT().add(target);

        LINK link = createLink(source, target, "CONNAIT", new DataPackAttribute("DEPUIS", "2024-01-15"));
        data.getLINKS().getLINK().add(link);

        ValidationResult result = new DataValidator().validate(data);

        Assert.assertTrue(result.isValid());
        Assert.assertEquals("COLLEGUE", getAttributeValue(link, "TYPE_RELATION"));
    }

    @Test
    public void validate_shouldRejectUnknownLinkAttributesDuringImport() {
        DATAS data = new DATAS();
        data.setOBJECTS(new OBJECTS());
        data.setLINKS(new LINKS());

        DataPackObject source = createPerson(1, "Dupont", "Alice", null);
        DataPackObject target = createPerson(2, "Martin", "Bob", null);
        data.getOBJECTS().getOBJECT().add(source);
        data.getOBJECTS().getOBJECT().add(target);

        LINK link = createLink(
            source,
            target,
            "CONNAIT",
            new DataPackAttribute("DEPUIS", "2024-01-15"),
            new DataPackAttribute("INCONNU", "x")
        );
        data.getLINKS().getLINK().add(link);

        ValidationResult result = new DataValidator().validate(data);

        Assert.assertFalse(result.isValid());
        Assert.assertTrue(containsErrorMessage(result, "Unknown attribute 'INCONNU'"));
        Assert.assertTrue(containsErrorMessage(result, "Allowed attributes: DEPUIS, TYPE_RELATION"));
    }

    @Test
    public void validateLink_shouldApplyDefaultsForCreateLinkFlow() {
        DataPackObject source = createPerson(10, "Durand", "Emma", null);
        DataPackObject target = createPerson(11, "Bernard", "Leo", null);
        LINK link = createLink(source, target, "CONNAIT", new DataPackAttribute("DEPUIS", "2024-03-01"));

        List<OBJECT> availableObjects = new ArrayList<>();
        availableObjects.add(createObjectRef(source.getID(), source.getTYPE()));
        availableObjects.add(createObjectRef(target.getID(), target.getTYPE()));

        ValidationResult result = new DataValidator().validateLink(link, availableObjects);

        Assert.assertTrue(result.isValid());
        Assert.assertEquals("COLLEGUE", getAttributeValue(link, "TYPE_RELATION"));
    }

    @Test
    public void validate_shouldHandleLinksWhenObjectsSectionIsMissing() {
        DATAS data = new DATAS();
        data.setLINKS(new LINKS());

        DataPackObject source = new DataPackObject();
        source.setID(1);
        source.setTYPE("PERSONNE");

        DataPackObject target = new DataPackObject();
        target.setID(2);
        target.setTYPE("PERSONNE");

        LINK link = createLink(source, target, "CONNAIT", new DataPackAttribute("DEPUIS", "2024-01-15"));
        data.getLINKS().getLINK().add(link);

        ValidationResult result = new DataValidator().validate(data);

        Assert.assertFalse(result.isValid());
        Assert.assertTrue(containsErrorMessage(result, "Source object not found in payload"));
        Assert.assertTrue(containsErrorMessage(result, "Target object not found in payload"));
    }

    private boolean containsErrorMessage(ValidationResult result, String expectedFragment) {
        for (ValidationError error : result.getErrors()) {
            String rendered = error.toString();
            if (rendered != null && rendered.contains(expectedFragment)) {
                return true;
            }
        }
        return false;
    }

    private DataPackObject createPerson(int id, String lastName, String firstName, String age) {
        DataPackObject person = new DataPackObject();
        person.setID(id);
        person.setTYPE("PERSONNE");
        if (lastName != null) {
            person.getATTRIBUTE().add(new DataPackAttribute("NOM", lastName));
        }
        if (firstName != null) {
            person.getATTRIBUTE().add(new DataPackAttribute("PRENOM", firstName));
        }
        if (age != null) {
            person.getATTRIBUTE().add(new DataPackAttribute("AGE", age));
        }
        return person;
    }

    private OBJECT createObjectRef(int id, String type) {
        OBJECT object = new OBJECT();
        object.setID(id);
        object.setTYPE(type);
        return object;
    }

    private LINK createLink(DataPackObject source, DataPackObject target, String type, DataPackAttribute... attributes) {
        LINK link = new LINK();
        link.setTYPE(type);
        link.setOBJLINKA(DataPackDtoUtils.createObjLink(source));
        link.setOBJLINKB(DataPackDtoUtils.createObjLink(target));
        if (attributes != null) {
            for (DataPackAttribute attribute : attributes) {
                link.getATTRIBUTE().add(attribute);
            }
        }
        return link;
    }

    private String getAttributeValue(LINK link, String key) {
        for (var attribute : link.getATTRIBUTE()) {
            if (attribute != null && key.equals(attribute.getKEY())) {
                return attribute.getVALUE();
            }
        }
        return null;
    }
}
