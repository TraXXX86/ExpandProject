package fr.expand.project.importdata.access;

import static org.junit.Assert.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;

public class WorkflowPermissionTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void migrationPreservesOldRightsWithoutGrantingTransitions() throws Exception {
        Path path = temp.newFile("legacy.sqlite").toPath();
        try (AccessControlStore store = new AccessControlStore(path, "test-bootstrap-secret")) {
            store.replaceModelPermissions(
                    "admin",
                    List.of(
                            Map.of(
                                    "modelKey",
                                    "model",
                                    "visible",
                                    true,
                                    "canRead",
                                    true,
                                    "canUpdate",
                                    true)));
        }
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
                Statement s = c.createStatement()) {
            s.execute("ALTER TABLE model_permissions DROP COLUMN can_transition");
        }
        try (AccessControlStore store = new AccessControlStore(path, null)) {
            var permission = store.listModelPermissions("admin").get(0);
            assertEquals(true, permission.get("canUpdate"));
            assertEquals(false, permission.get("canTransition"));
            store.replaceModelPermissions(
                    "admin",
                    List.of(
                            Map.of(
                                    "modelKey",
                                    "model",
                                    "visible",
                                    true,
                                    "canRead",
                                    true,
                                    "canTransition",
                                    true)));
        }
        try (AccessControlStore store = new AccessControlStore(path, null)) {
            assertEquals(true, store.listModelPermissions("admin").get(0).get("canTransition"));
            store.replaceModelPermissions(
                    "admin",
                    List.of(Map.of("modelKey", "model", "visible", true, "canRead", true)));
            assertEquals(false, store.listModelPermissions("admin").get(0).get("canTransition"));
        }
    }

    private AccessContext context(
            boolean actorAdmin, boolean effectiveAdmin, Map<String, Object> permission) {
        return new AccessContext(
                "token",
                Map.of("username", "actor", "platformAdmin", actorAdmin),
                Map.of(
                        "username",
                        "effective",
                        "portalUser",
                        true,
                        "platformAdmin",
                        effectiveAdmin),
                List.of(permission),
                true,
                100);
    }

    @Test
    public void transitionIsIndependentOfAttributeUpdateAndRequiresReadVisibility() {
        var permission =
                new HashMap<String, Object>(
                        Map.of(
                                "modelKey",
                                "model",
                                "visible",
                                true,
                                "canRead",
                                true,
                                "canUpdate",
                                true));
        assertFalse(context(false, false, permission).canTransition("model"));
        permission.put("canUpdate", false);
        permission.put("canTransition", true);
        var access = context(false, false, permission);
        assertTrue(access.canTransition("model"));
        assertFalse(access.canUpdateData("model"));
        assertFalse(access.canTransition("other"));
        permission.put("visible", false);
        assertFalse(context(false, false, permission).canTransition("model"));
        permission.put("visible", true);
        permission.put("canRead", false);
        assertFalse(context(false, false, permission).canTransition("model"));
    }

    @Test
    public void impersonationUsesEffectiveTransitionPermission() {
        var permission =
                Map.<String, Object>of(
                        "modelKey", "model", "visible", true, "canRead", true, "canUpdate", true);
        assertFalse(context(true, false, permission).canTransition("model"));
        assertTrue(context(false, true, permission).canTransition("model"));
    }
}
