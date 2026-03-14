package fr.expand.project.importdata;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import fr.expand.project.commons.ObjectTypeEnum;
import fr.expand.project.importdata.api.impl.ModelBasedImportAPI;
import fr.expand.project.importdata.dao.IConnectorDb;
import fr.expand.project.importdata.dto.DataPackAttribute;
import fr.expand.project.importdata.dto.DataPackObject;
import fr.expand.project.importdata.dto.generated.DATAS;
import fr.expand.project.importdata.dto.generated.LINK;
import fr.expand.project.importdata.dto.generated.LINKS;
import fr.expand.project.importdata.dto.generated.OBJECTS;
import fr.expand.project.importdata.dto.util.DataPackDtoUtils;
import fr.expand.project.importdata.model.ModelManager;
import fr.expand.project.importdata.validation.ValidationResult;

public class ModelBasedImportAPITests {

    @Before
    public void setupModel() throws Exception {
        ModelManager manager = ModelManager.getInstance();
        manager.clearModels();
        manager.loadModelFromResource("model/example_social_network_model.xml");
    }

    @Test
    public void importData_shouldForwardLinkAttributesToConnector() {
        DATAS data = new DATAS();
        data.setOBJECTS(new OBJECTS());
        data.setLINKS(new LINKS());

        DataPackObject person = new DataPackObject();
        person.setID(1);
        person.setTYPE("PERSONNE");
        person.getATTRIBUTE().add(new DataPackAttribute("NOM", "Durand"));
        person.getATTRIBUTE().add(new DataPackAttribute("PRENOM", "Alice"));
        data.getOBJECTS().getOBJECT().add(person);

        DataPackObject company = new DataPackObject();
        company.setID(2);
        company.setTYPE("ENTREPRISE");
        company.getATTRIBUTE().add(new DataPackAttribute("NOM", "Expand"));
        data.getOBJECTS().getOBJECT().add(company);

        LINK link = new LINK();
        link.setTYPE("TRAVAILLE_POUR");
        link.setOBJLINKA(DataPackDtoUtils.createObjLink(person));
        link.setOBJLINKB(DataPackDtoUtils.createObjLink(company));
        link.getATTRIBUTE().add(new DataPackAttribute("POSTE", "Architecte"));
        data.getLINKS().getLINK().add(link);

        RecordingConnector connector = new RecordingConnector();
        ModelBasedImportAPI api = new ModelBasedImportAPI();
        api.setConnector(connector);

        ValidationResult result = api.importData(data, false, "model-test");

        Assert.assertTrue(result.isValid());
        Assert.assertEquals("model-test", connector.getModelKey());
        Assert.assertEquals(1, connector.linkWriteCount);
        Assert.assertEquals("TRAVAILLE_POUR", connector.lastLinkType);
        Assert.assertEquals(1, connector.lastLinkAttributes.size());
        Assert.assertEquals("POSTE", connector.lastLinkAttributes.get(0).getKEY());
        Assert.assertEquals("Architecte", connector.lastLinkAttributes.get(0).getVALUE());
    }

    private static class RecordingConnector extends IConnectorDb {
        private int linkWriteCount;
        private String lastLinkType;
        private List<DataPackAttribute> lastLinkAttributes = new ArrayList<>();

        @Override
        protected void connectToDb() {
            // No external dependency for unit tests.
        }

        @Override
        protected void closeConnection() {
            // No-op.
        }

        @Override
        public int writeObject(DataPackObject object) {
            return object.getID() > 0 ? object.getID() : 1;
        }

        @Override
        public int writeLink(DataPackObject objectA, DataPackObject objectB, boolean isOriented, String linkType) {
            this.linkWriteCount++;
            this.lastLinkType = linkType;
            this.lastLinkAttributes = new ArrayList<>();
            return 1;
        }

        @Override
        public int writeLink(
            DataPackObject objectA,
            DataPackObject objectB,
            boolean isOriented,
            String linkType,
            List<DataPackAttribute> attributes
        ) {
            this.linkWriteCount++;
            this.lastLinkType = linkType;
            this.lastLinkAttributes = new ArrayList<>(attributes == null ? List.of() : attributes);
            return 1;
        }

        @Override
        public DataPackObject getObjectToDbDto(ObjectTypeEnum typeObject, int idObject) {
            return null;
        }

        @Override
        public void deleteAll() {
            // No-op.
        }
    }
}
