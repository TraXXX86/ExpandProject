package fr.expand.project.importdata;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import fr.expand.project.importdata.model.ModelManager;

public class ModelManagerContextTests {

    @Before
    public void resetManager() {
        ModelManager.getInstance().clearModels();
    }

    @Test
    public void loadModelContext_shouldNotReplaceCurrentModel() throws Exception {
        ModelManager manager = ModelManager.getInstance();
        manager.loadModelFromResource("model/example_social_network_model.xml");

        Assert.assertNotNull(manager.getCurrentContext());
        Assert.assertEquals("SocialNetworkModel", manager.getCurrentModel().getNAME());

        ModelManager.ModelContext requestContext =
            manager.loadModelContextFromResource("model/example_auto_refurb_model.xml");

        Assert.assertNotNull(requestContext);
        Assert.assertEquals("AutoRefurbModel", requestContext.getModel().getNAME());
        Assert.assertEquals("SocialNetworkModel", manager.getCurrentModel().getNAME());
        Assert.assertEquals("SocialNetworkModel", manager.getCurrentContext().getModel().getNAME());
    }
}
