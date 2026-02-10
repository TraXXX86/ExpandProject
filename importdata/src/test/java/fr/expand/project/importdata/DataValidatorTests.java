package fr.expand.project.importdata;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import fr.expand.project.importdata.dto.DataPackAttribute;
import fr.expand.project.importdata.dto.DataPackObject;
import fr.expand.project.importdata.dto.generated.DATAS;
import fr.expand.project.importdata.dto.generated.LINK;
import fr.expand.project.importdata.dto.generated.LINKS;
import fr.expand.project.importdata.dto.generated.OBJECTS;
import fr.expand.project.importdata.dto.util.DataPackDtoUtils;
import fr.expand.project.importdata.model.ModelManager;
import fr.expand.project.importdata.validation.DataValidator;
import fr.expand.project.importdata.validation.ValidationResult;

public class DataValidatorTests {

    @Before
    public void setupModel() throws Exception {
        ModelManager manager = ModelManager.getInstance();
        manager.clearModels();
        manager.loadModelFromResource("model/example_social_network_model.xml");
    }

    @Test
    public void validate_shouldDetectMissingRequiredAttribute() {
        DATAS data = new DATAS();
        data.setOBJECTS(new OBJECTS());

        DataPackObject person = new DataPackObject();
        person.setID(1);
        person.setTYPE("PERSONNE");
        person.getATTRIBUTE().add(new DataPackAttribute("NOM", "Dupont"));
        data.getOBJECTS().getOBJECT().add(person);

        ValidationResult result = new DataValidator().validate(data);

        Assert.assertTrue(result.hasErrors());
        Assert.assertTrue(result.getErrorMessages().stream()
                .anyMatch(message -> message.contains("Missing required attribute: PRENOM")));
    }

    @Test
    public void validate_shouldDetectInvalidAttributeType() {
        DATAS data = new DATAS();
        data.setOBJECTS(new OBJECTS());

        DataPackObject person = new DataPackObject();
        person.setID(2);
        person.setTYPE("PERSONNE");
        person.getATTRIBUTE().add(new DataPackAttribute("NOM", "Martin"));
        person.getATTRIBUTE().add(new DataPackAttribute("PRENOM", "Alice"));
        person.getATTRIBUTE().add(new DataPackAttribute("AGE", "vingt-cinq"));
        data.getOBJECTS().getOBJECT().add(person);

        ValidationResult result = new DataValidator().validate(data);

        Assert.assertTrue(result.hasErrors());
        Assert.assertTrue(result.getErrorMessages().stream()
                .anyMatch(message -> message.contains("Invalid type for attribute 'AGE': expected INTEGER")));
    }

    @Test
    public void validate_shouldHandleLinksWhenObjectsSectionIsMissing() {
        DATAS data = new DATAS();
        data.setLINKS(new LINKS());

        LINK link = new LINK();
        link.setTYPE("CONNAIT");

        DataPackObject source = new DataPackObject();
        source.setID(1);
        source.setTYPE("PERSONNE");

        DataPackObject target = new DataPackObject();
        target.setID(2);
        target.setTYPE("PERSONNE");

        link.setOBJLINKA(DataPackDtoUtils.createObjLink(source));
        link.setOBJLINKB(DataPackDtoUtils.createObjLink(target));
        data.getLINKS().getLINK().add(link);

        ValidationResult result = new DataValidator().validate(data);

        Assert.assertTrue(result.hasErrors());
        Assert.assertTrue(result.getErrorMessages().stream().anyMatch(message -> message.contains("Source object not found")));
        Assert.assertTrue(result.getErrorMessages().stream().anyMatch(message -> message.contains("Target object not found")));
    }
}
