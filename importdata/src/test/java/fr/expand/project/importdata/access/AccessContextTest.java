package fr.expand.project.importdata.access;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.List;
import java.util.Map;

public class AccessContextTest {
    private Map<String, Object> user(String name, boolean modelAdmin, boolean platformAdmin) {
        return Map.of(
                "username",
                name,
                "displayName",
                name,
                "portalUser",
                true,
                "portalModelAdmin",
                modelAdmin,
                "platformAdmin",
                platformAdmin);
    }

    @Test
    public void impersonationUsesEffectiveUsersPermissionsWithoutActorAdminBypass() {
        AccessContext context =
                new AccessContext(
                        "token",
                        user("admin", true, true),
                        user("reader", false, false),
                        List.of(
                                Map.of(
                                        "modelKey",
                                        "model",
                                        "visible",
                                        true,
                                        "canRead",
                                        true,
                                        "canCreate",
                                        false,
                                        "canUpdate",
                                        false,
                                        "canDelete",
                                        false)),
                        true,
                        100);
        assertTrue(context.isActorPlatformAdmin());
        assertTrue(context.isImpersonating());
        assertFalse(context.isPlatformAdmin());
        assertFalse(context.isPortalModelAdmin());
        assertTrue(context.canReadData("model"));
        assertFalse(context.canCreateData("model"));
        assertFalse(context.canUpdateData("model"));
        assertFalse(context.canDeleteData("model"));
        assertFalse(context.canViewModel("unassigned"));
    }

    @Test
    public void visibilityAloneDoesNotGrantMutationAndHiddenModelsFailClosed() {
        Map<String, Object> modelAdmin = user("model-admin", true, false);
        AccessContext context =
                new AccessContext(
                        "token",
                        modelAdmin,
                        modelAdmin,
                        List.of(
                                Map.of("modelKey", "visible", "visible", true),
                                Map.of(
                                        "modelKey",
                                        "hidden",
                                        "visible",
                                        false,
                                        "canRead",
                                        true,
                                        "canCreate",
                                        true,
                                        "canUpdate",
                                        true,
                                        "canDelete",
                                        true)),
                        false,
                        100);
        assertTrue(context.isPortalModelAdmin());
        assertTrue(context.canViewModel("visible"));
        assertFalse(context.canUpdateData("visible"));
        assertFalse(context.canDeleteData("visible"));
        assertFalse(context.canReadData("hidden"));
        assertFalse(context.canCreateData("hidden"));
        assertFalse(context.canUpdateData("hidden"));
        assertFalse(context.canDeleteData("hidden"));
    }

    @Test
    public void effectivePlatformAdminRetainsGlobalPermission() {
        Map<String, Object> admin = user("admin", false, true);
        AccessContext context = new AccessContext("token", admin, admin, List.of(), false, 100);
        assertTrue(context.isPortalUser());
        assertTrue(context.isPortalModelAdmin());
        assertTrue(context.canViewModel("unassigned"));
        assertTrue(context.canReadData("unassigned"));
        assertTrue(context.canCreateData("unassigned"));
        assertTrue(context.canUpdateData("unassigned"));
        assertTrue(context.canDeleteData("unassigned"));
    }
}
