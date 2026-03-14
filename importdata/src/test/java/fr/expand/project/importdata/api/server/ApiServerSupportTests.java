package fr.expand.project.importdata.api.server;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import fr.expand.project.importdata.model.ModelManager;
import fr.expand.project.importdata.model.generated.DATAMODEL;

public class ApiServerSupportTests {

    private final ApiSupport apiSupport = new ApiSupport();
    private final ModelPayloadMapper modelPayloadMapper = new ModelPayloadMapper();

    @Before
    public void clearModels() {
        ModelManager.getInstance().clearModels();
    }

    @Test
    public void sanitizeUsername_shouldTrimAndRejectInvalidCharacters() {
        Assert.assertEquals("alice.admin", apiSupport.sanitizeUsername("  alice.admin  "));
        Assert.assertNull(apiSupport.sanitizeUsername("alice admin"));
        Assert.assertNull(apiSupport.sanitizeUsername("alice/admin"));
        Assert.assertNull(apiSupport.sanitizeUsername("   "));
    }

    @Test
    public void readPermissionList_shouldNormalizePermissionRows() {
        List<Map<String, Object>> permissions = apiSupport.readPermissionList(List.of(
            Map.of(
                "modelKey", "social",
                "visible", true,
                "canRead", 1,
                "canCreate", "true",
                "canUpdate", 0,
                "canDelete", false
            ),
            Map.of("visible", true)
        ));

        Assert.assertEquals(1, permissions.size());
        Map<String, Object> permission = permissions.get(0);
        Assert.assertEquals("social", permission.get("modelKey"));
        Assert.assertEquals(Boolean.TRUE, permission.get("visible"));
        Assert.assertEquals(Boolean.TRUE, permission.get("canRead"));
        Assert.assertEquals(Boolean.TRUE, permission.get("canCreate"));
        Assert.assertEquals(Boolean.FALSE, permission.get("canUpdate"));
        Assert.assertEquals(Boolean.FALSE, permission.get("canDelete"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void buildModelDetails_shouldExposeModelSummaryAndCounts() throws Exception {
        DATAMODEL model = ModelManager.getInstance().loadModelFromResource("model/example_social_network_model.xml");

        Map<String, Object> payload = modelPayloadMapper.buildModelDetails("social", model);

        Assert.assertEquals("social", payload.get("key"));
        Assert.assertEquals(model.getNAME(), payload.get("name"));
        Assert.assertEquals(model.getVERSION(), payload.get("version"));
        Assert.assertEquals(model.getDEFAULTLANGUAGE(), payload.get("defaultLanguage"));
        Assert.assertEquals("Social Network Portal", ((Map<String, String>) payload.get("userPortalLabels")).get("EN_uk"));

        List<Map<String, Object>> languages = (List<Map<String, Object>>) payload.get("languages");
        List<Map<String, Object>> objectTypes = (List<Map<String, Object>>) payload.get("objectTypes");
        List<Map<String, Object>> linkTypes = (List<Map<String, Object>>) payload.get("linkTypes");

        Assert.assertEquals(model.getLANGUAGES().getLANGUAGE().size(), languages.size());
        Assert.assertEquals(model.getOBJECTTYPES().getOBJECTTYPE().size(), payload.get("objectTypeCount"));
        Assert.assertEquals(model.getOBJECTTYPES().getOBJECTTYPE().size(), objectTypes.size());
        Assert.assertEquals(model.getLINKTYPES().getLINKTYPE().size(), payload.get("linkTypeCount"));
        Assert.assertEquals(model.getLINKTYPES().getLINKTYPE().size(), linkTypes.size());
        Assert.assertFalse(((List<Map<String, Object>>) objectTypes.get(0).get("attributes")).isEmpty());
    }
}
