package fr.expand.project.importdata;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import fr.expand.project.importdata.access.AccessContext;

public class AccessContextTests {

    @Test
    public void permissions_shouldRequireVisibilityForDataOperations() {
        AccessContext context = new AccessContext(
            "session-1",
            user("admin", true, true, true),
            user("reader", true, false, false),
            List.of(permission("social", false, true, true, true, true)),
            false,
            42L
        );

        Assert.assertFalse(context.canViewModel("social"));
        Assert.assertFalse(context.canReadData("social"));
        Assert.assertFalse(context.canCreateData("social"));
        Assert.assertFalse(context.canUpdateData("social"));
        Assert.assertFalse(context.canDeleteData("social"));
    }

    @Test
    public void platformAdmin_shouldBypassExplicitModelPermissions() {
        AccessContext context = new AccessContext(
            "session-2",
            user("admin", true, true, true),
            user("platform-admin", false, false, true),
            List.of(),
            false,
            84L
        );

        Assert.assertTrue(context.isPortalUser());
        Assert.assertTrue(context.isPortalModelAdmin());
        Assert.assertTrue(context.canViewModel("finance"));
        Assert.assertTrue(context.canReadData("finance"));
        Assert.assertTrue(context.canCreateData("finance"));
        Assert.assertTrue(context.canUpdateData("finance"));
        Assert.assertTrue(context.canDeleteData("finance"));
    }

    @Test
    public void extractBearerToken_shouldAcceptCaseInsensitivePrefixAndTrimSpaces() {
        Assert.assertEquals("abc123", AccessContext.extractBearerToken("Bearer abc123"));
        Assert.assertEquals("abc123", AccessContext.extractBearerToken("bearer   abc123   "));
        Assert.assertNull(AccessContext.extractBearerToken("Basic abc123"));
        Assert.assertNull(AccessContext.extractBearerToken("Bearer   "));
        Assert.assertNull(AccessContext.extractBearerToken(null));
    }

    private static Map<String, Object> user(String username, boolean portalUser, boolean portalModelAdmin,
        boolean platformAdmin) {
        return Map.of(
            "username", username,
            "displayName", username,
            "portalUser", portalUser,
            "portalModelAdmin", portalModelAdmin,
            "platformAdmin", platformAdmin
        );
    }

    private static Map<String, Object> permission(String modelKey, boolean visible, boolean canRead,
        boolean canCreate, boolean canUpdate, boolean canDelete) {
        return Map.of(
            "modelKey", modelKey,
            "visible", visible,
            "canRead", canRead,
            "canCreate", canCreate,
            "canUpdate", canUpdate,
            "canDelete", canDelete
        );
    }
}
