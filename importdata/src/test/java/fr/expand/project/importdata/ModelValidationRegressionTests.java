package fr.expand.project.importdata;

import fr.expand.project.importdata.dto.generated.ATTRIBUTE;
import fr.expand.project.importdata.dto.generated.DATAS;
import fr.expand.project.importdata.dto.generated.LINK;
import fr.expand.project.importdata.model.ModelManager;
import fr.expand.project.importdata.model.generated.ATTRIBUTETYPE;
import fr.expand.project.importdata.validation.AttributeValues;
import fr.expand.project.importdata.validation.DataValidator;
import fr.expand.project.importdata.validation.ValidationResult;
import fr.expand.project.importdata.xml.XmlSupport;

import jakarta.xml.bind.JAXBException;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

public class ModelValidationRegressionTests {
    private static final String MODEL =
            """
<DATA_MODEL NAME="Test" VERSION="1">
  <OBJECT_TYPES><OBJECT_TYPE NAME="Person"><ATTRIBUTE_DEFINITIONS>
    <ATTRIBUTE_DEFINITION NAME="name" REQUIRED="true"/>
    <ATTRIBUTE_DEFINITION NAME="age" TYPE="INTEGER"/>
    <ATTRIBUTE_DEFINITION NAME="status" DEFAULT_VALUE="active"/>
  </ATTRIBUTE_DEFINITIONS></OBJECT_TYPE></OBJECT_TYPES>
  <LINK_TYPES><LINK_TYPE NAME="Knows">
    <SOURCE_TYPES><TYPE_REF NAME="Person"/></SOURCE_TYPES>
    <TARGET_TYPES><TYPE_REF NAME="Person"/></TARGET_TYPES>
    <ATTRIBUTE_DEFINITIONS>
      <ATTRIBUTE_DEFINITION NAME="weight" TYPE="DOUBLE" REQUIRED="true" DEFAULT_VALUE="1.5"/>
      <ATTRIBUTE_DEFINITION NAME="since" TYPE="DATE"/>
    </ATTRIBUTE_DEFINITIONS>
  </LINK_TYPE></LINK_TYPES>
</DATA_MODEL>
""";
    private static final String OBJECTS =
            """
            <OBJECTS>
              <OBJECT ID="0" TYPE="Person"><ATTRIBUTE KEY="name" VALUE="Ada"/></OBJECT>
              <OBJECT ID="1" TYPE="Person"><ATTRIBUTE KEY="name" VALUE="Ben"/></OBJECT>
            </OBJECTS>
            """;
    private static final String LINK =
            """
            <LINK TYPE="Knows">%s
              <OBJ_LINK_A ID="0" TYPE="Person"/><OBJ_LINK_B ID="1" TYPE="Person"/>
            </LINK>
            """;

    private static ModelManager manager() throws JAXBException {
        ModelManager manager = new ModelManager();
        manager.loadModelFromXml(MODEL);
        return manager;
    }

    private static DATAS data(String objects, String links) throws JAXBException {
        return XmlSupport.parseData("<DATAS>" + objects + "<LINKS>" + links + "</LINKS></DATAS>");
    }

    private static void assertXmlRejected(String xml, boolean model) throws Exception {
        try {
            if (model) {
                new ModelManager().loadModelFromXml(xml);
            } else {
                XmlSupport.parseData(xml);
            }
            Assert.fail("Invalid XML was accepted");
        } catch (JAXBException expected) {
            Assert.assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void failedModelLoadsKeepPublishedModelAndXml() throws Exception {
        ModelManager manager = manager();
        var before = manager.getCurrentModel();
        String beforeXml = manager.getCurrentModelXml();
        String invalid =
                MODEL.replace(
                        "<OBJECT_TYPE NAME=\"Person\">",
                        "<OBJECT_TYPE NAME=\"Person\" PARENT=\"Missing\">");
        try {
            manager.loadModelFromXml(invalid);
            Assert.fail("Unknown parent was accepted");
        } catch (JAXBException expected) {
        }
        Assert.assertSame(before, manager.getCurrentModel());
        Assert.assertEquals(beforeXml, manager.getCurrentModelXml());
        Assert.assertEquals(1, manager.getLoadedModels().size());
    }

    @Test
    public void versionsRemainIndependentlySelectable() throws Exception {
        ModelManager manager = manager();
        manager.loadModelFromXml(MODEL.replace("VERSION=\"1\"", "VERSION=\"2\""));
        Assert.assertEquals(2, manager.getLoadedModels().size());
        Assert.assertTrue(manager.setCurrentModel("Test", "1"));
        Assert.assertEquals("1", manager.getCurrentModel().getVERSION());
        Assert.assertEquals(MODEL, manager.getCurrentModelXml());
        Assert.assertFalse(manager.setCurrentModel("Test", "missing"));
        Assert.assertEquals("1", manager.getCurrentModel().getVERSION());
    }

    @Test
    public void requestManagersKeepConcurrentModelsSeparate() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<String> first =
                    () -> {
                        ModelManager manager = manager();
                        return manager.getCurrentModel().getNAME();
                    };
            Callable<String> second =
                    () -> {
                        ModelManager manager = new ModelManager();
                        manager.loadModelFromXml(MODEL.replace("NAME=\"Test\"", "NAME=\"Other\""));
                        return manager.getCurrentModel().getNAME();
                    };
            var a = executor.submit(first);
            var b = executor.submit(second);
            Assert.assertEquals("Test", a.get());
            Assert.assertEquals("Other", b.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void rejectsDtdAndExternalEntitiesInBothXmlFormats() throws Exception {
        Path secret = Files.createTempFile("xml-secret", ".txt");
        try {
            Files.writeString(secret, "should-never-be-read");
            String dtd = "<!DOCTYPE DATAS [<!ENTITY secret SYSTEM '" + secret.toUri() + "'>]>";
            assertXmlRejected(dtd + "<DATAS><OBJECTS/><LINKS/></DATAS>", false);
            assertXmlRejected(
                    "<!DOCTYPE DATA_MODEL SYSTEM '" + secret.toUri() + "'>" + MODEL, true);
            assertXmlRejected(
                    "<!DOCTYPE DATA_MODEL [<!ENTITY secret SYSTEM '"
                            + secret.toUri()
                            + "'>]>"
                            + MODEL.replace("NAME=\"Test\"", "NAME=\"&secret;\""),
                    true);
        } finally {
            Files.deleteIfExists(secret);
        }
    }

    @Test
    public void rejectsMalformedMissingAndUnknownSchemaContent() throws Exception {
        assertXmlRejected("<DATAS>", false);
        assertXmlRejected(
                "<DATAS><OBJECTS><OBJECT TYPE=\"Person\"/></OBJECTS><LINKS/></DATAS>", false);
        assertXmlRejected("<DATAS><OBJECTS><OBJECT ID=\"1\"/></OBJECTS><LINKS/></DATAS>", false);
        assertXmlRejected("<DATAS><OBJECTS/><LINKS/><SURPRISE/></DATAS>", false);
        assertXmlRejected(MODEL.replace("TYPE=\"INTEGER\"", "TYPE=\"UNKNOWN\""), true);
        assertXmlRejected(MODEL.replace("NAME=\"Test\"", "NAME=\"   \""), true);
    }

    @Test
    public void permitsEmptyAndSingleLinkSections() throws Exception {
        Assert.assertTrue(new DataValidator(manager()).validate(data(OBJECTS, "")).isValid());
        Assert.assertTrue(
                new DataValidator(manager()).validate(data(OBJECTS, LINK.formatted(""))).isValid());
    }

    @Test
    public void rejectsCyclesDuplicateTypesDefinitionsAndUnknownReferences() throws Exception {
        assertXmlRejected(
                MODEL.replace(
                        "<OBJECT_TYPE NAME=\"Person\">",
                        "<OBJECT_TYPE NAME=\"Person\" PARENT=\"Person\">"),
                true);
        assertXmlRejected(
                MODEL.replace("</OBJECT_TYPES>", "<OBJECT_TYPE NAME=\"Person\"/></OBJECT_TYPES>"),
                true);
        assertXmlRejected(
                MODEL.replace(
                        "<ATTRIBUTE_DEFINITION NAME=\"age\" TYPE=\"INTEGER\"/>",
                        "<ATTRIBUTE_DEFINITION NAME=\"name\"/>"),
                true);
        assertXmlRejected(
                MODEL.replace("<TYPE_REF NAME=\"Person\"/>", "<TYPE_REF NAME=\"Missing\"/>"), true);
        assertXmlRejected(
                MODEL.replace(
                        "</OBJECT_TYPE>",
                        "<ATTRIBUTE_GROUPS><ATTRIBUTE_GROUP NAME=\"g\"><ATTRIBUTE_REF"
                            + " NAME=\"missing\"/>"
                            + "</ATTRIBUTE_GROUP></ATTRIBUTE_GROUPS></OBJECT_TYPE>"),
                true);
        assertXmlRejected(
                MODEL.replace(
                        "</OBJECT_TYPE>",
                        "<REPRESENTATIVE_ATTRIBUTES><ATTRIBUTE_REF NAME=\"missing\"/>"
                                + "</REPRESENTATIVE_ATTRIBUTES></OBJECT_TYPE>"),
                true);
    }

    @Test
    public void rejectsReservedModelAttributesAndBadDefaults() throws Exception {
        for (String key :
                new String[] {
                    "modelKey",
                    "dataId",
                    "uuid",
                    "TYPE",
                    "_uuid",
                    "searchText",
                    "SearchText",
                    "SEARCHTEXT"
                }) {
            assertXmlRejected(MODEL.replace("NAME=\"status\"", "NAME=\"" + key + "\""), true);
        }
        assertXmlRejected(MODEL.replace("DEFAULT_VALUE=\"1.5\"", "DEFAULT_VALUE=\"NaN\""), true);
        assertXmlRejected(
                MODEL.replace(
                        "NAME=\"age\" TYPE=\"INTEGER\"",
                        "NAME=\"age\" TYPE=\"INTEGER\" DEFAULT_VALUE=\"no\""),
                true);
    }

    @Test
    public void rejectsGraphLabelsReservedForModelMetadata() throws Exception {
        for (String type :
                new String[] {
                    "DataObject",
                    "DataModel",
                    "ModelObjectType",
                    "ModelLinkType",
                    "ModelAttribute",
                    "datamodel"
                }) {
            assertXmlRejected(MODEL.replace("NAME=\"Person\"", "NAME=\"" + type + "\""), true);
        }
    }

    @Test
    public void validatesRealCalendarDatesAndFiniteNumbers() {
        for (String date :
                new String[] {
                    "2024-02-29",
                    "2024-02-29T14:30:00",
                    "2024-02-29T14:30:00Z",
                    "2024-02-29T14:30:00+02:00"
                }) {
            Assert.assertTrue(date, AttributeValues.isValid(date, ATTRIBUTETYPE.DATE));
        }
        for (String date :
                new String[] {
                    "2023-02-29",
                    "2024-02-30",
                    "2024-13-01",
                    "2024-01-01junk",
                    "2024-01-01T25:00:00",
                    ""
                }) {
            Assert.assertFalse(date, AttributeValues.isValid(date, ATTRIBUTETYPE.DATE));
        }
        for (String number : new String[] {"NaN", "Infinity", "-Infinity", "1e999"}) {
            Assert.assertFalse(number, AttributeValues.isValid(number, ATTRIBUTETYPE.DOUBLE));
        }
    }

    @Test
    public void appliesAndValidatesObjectAndLinkDefaultsOnce() throws Exception {
        DATAS data = data(OBJECTS, LINK.formatted(""));
        DataValidator validator = new DataValidator(manager());
        ValidationResult first = validator.validate(data);
        Assert.assertTrue(first.getReport(), first.isValid());
        Assert.assertTrue(validator.validate(data).isValid());
        Assert.assertEquals(
                "active", data.getOBJECTS().getOBJECT().get(0).getATTRIBUTE().get(1).getVALUE());
        Assert.assertEquals(
                "1.5", data.getLINKS().getLINK().get(0).getATTRIBUTE().get(0).getVALUE());
        Assert.assertEquals(2, data.getOBJECTS().getOBJECT().get(0).getATTRIBUTE().size());
        Assert.assertEquals(1, data.getLINKS().getLINK().get(0).getATTRIBUTE().size());
    }

    @Test
    public void requiredBlankAndDuplicateAttributesFail() throws Exception {
        DataValidator validator = new DataValidator(manager());
        DATAS blank = data(OBJECTS.replace("VALUE=\"Ada\"", "VALUE=\"   \""), "");
        Assert.assertFalse(validator.validate(blank).isValid());
        DATAS duplicate =
                data(
                        OBJECTS.replace(
                                "VALUE=\"Ada\"/>",
                                "VALUE=\"Ada\"/><ATTRIBUTE KEY=\"name\" VALUE=\"Other\"/>"),
                        "");
        Assert.assertFalse(validator.validate(duplicate).isValid());
    }

    @Test
    public void linkTypesRequiredAttributesAndEndpointsFailCleanly() throws Exception {
        DataValidator validator = new DataValidator(manager());
        DATAS invalid =
                data(
                        OBJECTS,
                        LINK.formatted(
                                "<ATTRIBUTE KEY=\"weight\" VALUE=\"NaN\"/><ATTRIBUTE KEY=\"since\""
                                    + " VALUE=\"2023-02-29\"/>"));
        Assert.assertEquals(2, validator.validate(invalid).getErrors().size());
        DATAS blank = data(OBJECTS, LINK.formatted("<ATTRIBUTE KEY=\"weight\" VALUE=\" \"/>"));
        Assert.assertFalse(validator.validate(blank).isValid());
        DATAS missing = data(OBJECTS, LINK.formatted(""));
        missing.getLINKS().getLINK().get(0).setOBJLINKA(null);
        missing.getLINKS().getLINK().get(0).setOBJLINKB(null);
        Assert.assertEquals(2, validator.validate(missing).getErrors().size());
    }

    @Test
    public void rejectsReservedUnknownAttributesAndDuplicateLinkAttributes() throws Exception {
        DataValidator validator = new DataValidator(manager());
        for (String key :
                new String[] {
                    "modelKey",
                    "dataId",
                    "uuid",
                    "TYPE",
                    "_uuid",
                    "searchText",
                    "SearchText",
                    "SEARCHTEXT"
                }) {
            DATAS submitted = data(OBJECTS, "");
            ATTRIBUTE attribute = new ATTRIBUTE();
            attribute.setKEY(key);
            attribute.setVALUE("overwrite");
            submitted.getOBJECTS().getOBJECT().get(0).getATTRIBUTE().add(attribute);
            Assert.assertFalse(key, validator.validate(submitted).isValid());
        }
        DATAS duplicate =
                data(
                        OBJECTS,
                        LINK.formatted(
                                "<ATTRIBUTE KEY=\"weight\" VALUE=\"1\"/><ATTRIBUTE KEY=\"weight\""
                                    + " VALUE=\"2\"/>"));
        Assert.assertFalse(validator.validate(duplicate).isValid());
    }

    @Test
    public void inheritanceSuppliesRequiredAttributesDefaultsAndAllowedEndpoints()
            throws Exception {
        ModelManager manager = new ModelManager();
        manager.loadModelFromXml(
                MODEL.replace(
                        "</OBJECT_TYPES>",
                        "<OBJECT_TYPE NAME=\"Employee\" PARENT=\"Person\"/></OBJECT_TYPES>"));
        DATAS data =
                data(
                        OBJECTS.replace("TYPE=\"Person\"", "TYPE=\"Employee\""),
                        LINK.formatted("").replace("TYPE=\"Person\"", "TYPE=\"Employee\""));
        Assert.assertTrue(new DataValidator(manager).validate(data).isValid());
        Assert.assertEquals(
                "active", data.getOBJECTS().getOBJECT().get(0).getATTRIBUTE().get(1).getVALUE());
    }

    @Test
    public void resultsRemainStableWhenValidatorIsReused() throws Exception {
        DataValidator validator = new DataValidator(manager());
        ValidationResult invalid = validator.validate(null);
        Assert.assertFalse(invalid.isValid());
        Assert.assertTrue(validator.validate(data(OBJECTS, "")).isValid());
        Assert.assertFalse(invalid.isValid());
        Assert.assertEquals(1, invalid.getErrors().size());
    }

    @Test
    public void standaloneAttributeValidationAppliesDefaultsWithoutEndpoints() throws Exception {
        DataValidator validator = new DataValidator(manager());
        LINK link = new LINK();
        link.setTYPE("Knows");
        Assert.assertTrue(validator.validateLinkAttributes(link).isValid());
        Assert.assertEquals("1.5", link.getATTRIBUTE().get(0).getVALUE());
        Assert.assertTrue(validator.validateLinkAttributes(link).isValid());
        Assert.assertEquals(1, link.getATTRIBUTE().size());
        link.getATTRIBUTE().get(0).setVALUE("Infinity");
        Assert.assertFalse(validator.validateLinkAttributes(link).isValid());

        DATAS data = data(OBJECTS, "");
        Assert.assertTrue(
                validator.validateObjectAttributes(data.getOBJECTS().getOBJECT().get(0)).isValid());
        Assert.assertEquals(
                "active", data.getOBJECTS().getOBJECT().get(0).getATTRIBUTE().get(1).getVALUE());
    }

    @Test
    public void linkMissingRequiredAttributeWithoutDefaultFails() throws Exception {
        ModelManager manager = new ModelManager();
        manager.loadModelFromXml(MODEL.replace(" DEFAULT_VALUE=\"1.5\"", ""));
        LINK link = new LINK();
        link.setTYPE("Knows");
        ValidationResult result = new DataValidator(manager).validateLinkAttributes(link);
        Assert.assertFalse(result.isValid());
        Assert.assertTrue(result.getReport().contains("Missing required attribute: weight"));
    }
}
