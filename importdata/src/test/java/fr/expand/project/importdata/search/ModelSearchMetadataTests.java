package fr.expand.project.importdata.search;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import fr.expand.project.importdata.model.ModelManager;

public class ModelSearchMetadataTests {

    @Before
    public void setupModel() throws Exception {
        ModelManager manager = ModelManager.getInstance();
        manager.clearModels();
        manager.loadModelFromResource("model/example_social_network_model.xml");
    }

    @Test
    public void from_shouldIncludeInheritedSearchableAttributesForSubtype() {
        ModelSearchMetadata metadata = ModelSearchMetadata.from(ModelManager.getInstance());

        Map<String, List<String>> searchableByType = metadata.getSearchableAttributesByType();
        List<String> employeeAttributes = searchableByType.get("EMPLOYE");

        Assert.assertNotNull(employeeAttributes);
        Assert.assertTrue(employeeAttributes.contains("PRENOM"));
        Assert.assertTrue(employeeAttributes.contains("NOM"));
        Assert.assertTrue(employeeAttributes.contains("POSTE"));
        Assert.assertFalse(employeeAttributes.contains("DATE_EMBAUCHE"));
    }

    @Test
    public void from_shouldExposeRepresentativeAttributesInDeclaredOrder() {
        ModelSearchMetadata metadata = ModelSearchMetadata.from(ModelManager.getInstance());

        Map<String, List<String>> representativeByType = metadata.getRepresentativeAttributesByType();

        Assert.assertEquals(
            List.of("PRENOM", "NOM", "POSTE"),
            representativeByType.get("EMPLOYE")
        );
        Assert.assertEquals(
            List.of("NOM", "SIRET"),
            representativeByType.get("ENTREPRISE")
        );
    }
}
