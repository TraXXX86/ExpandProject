package fr.expand.project.importdata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import fr.expand.project.importdata.access.AccessControlStore;

public class AccessControlStoreTests {

    private String previousAccessDbPath;
    private Path tempDirectory;

    @Before
    public void setupStorePath() throws IOException {
        previousAccessDbPath = System.getProperty("ACCESS_DB_PATH");
        tempDirectory = Files.createTempDirectory("expand-access-tests");
        System.setProperty("ACCESS_DB_PATH", tempDirectory.resolve("access.sqlite").toString());
    }

    @After
    public void cleanupStorePath() throws IOException {
        if (previousAccessDbPath == null) {
            System.clearProperty("ACCESS_DB_PATH");
        } else {
            System.setProperty("ACCESS_DB_PATH", previousAccessDbPath);
        }

        if (tempDirectory != null && Files.exists(tempDirectory)) {
            try (var paths = Files.walk(tempDirectory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // Best effort cleanup for temp files created by SQLite.
                    }
                });
            }
        }
    }

    @Test
    public void replaceModelPermissions_shouldTrimAndPersistNormalizedFlags() {
        try (AccessControlStore store = new AccessControlStore()) {
            store.upsertUser("reader", "Reader", true, false, false);

            Map<String, Object> validPermission = new HashMap<>();
            validPermission.put("modelKey", " social-network ");
            validPermission.put("visible", 1);
            validPermission.put("canRead", "true");
            validPermission.put("canCreate", 0);
            validPermission.put("canUpdate", false);
            validPermission.put("canDelete", "false");

            Map<String, Object> blankPermission = new HashMap<>();
            blankPermission.put("modelKey", "   ");
            blankPermission.put("visible", true);

            List<Map<String, Object>> permissions = new ArrayList<>();
            permissions.add(validPermission);
            permissions.add(blankPermission);
            permissions.add(null);

            store.replaceModelPermissions("reader", permissions);

            List<Map<String, Object>> persisted = store.listModelPermissions("reader");
            Assert.assertEquals(1, persisted.size());
            Assert.assertEquals("social-network", persisted.get(0).get("modelKey"));
            Assert.assertEquals(Boolean.TRUE, persisted.get(0).get("visible"));
            Assert.assertEquals(Boolean.TRUE, persisted.get(0).get("canRead"));
            Assert.assertEquals(Boolean.FALSE, persisted.get(0).get("canCreate"));
            Assert.assertEquals(Boolean.FALSE, persisted.get(0).get("canUpdate"));
            Assert.assertEquals(Boolean.FALSE, persisted.get(0).get("canDelete"));
        }
    }

    @Test
    public void impersonation_shouldRequirePlatformAdminAndRestoreActorWhenStopped() {
        try (AccessControlStore store = new AccessControlStore()) {
            store.upsertUser("reader", "Reader", true, false, false, "reader");
            store.upsertUser("target", "Target", true, false, false, "target");

            Map<String, Object> readerSession = store.createSession("reader");
            Assert.assertNotNull(readerSession);
            Assert.assertFalse(store.impersonateSession(readerSession.get("token").toString(), "target"));

            Map<String, Object> adminSession = store.createSession("admin");
            Assert.assertNotNull(adminSession);
            Assert.assertTrue(store.impersonateSession(adminSession.get("token").toString(), "target"));

            Map<String, Object> impersonated = store.loadSession(adminSession.get("token").toString());
            Assert.assertEquals("admin", impersonated.get("actorUsername"));
            Assert.assertEquals("target", impersonated.get("effectiveUsername"));
            Assert.assertEquals(Boolean.TRUE, impersonated.get("impersonating"));

            Assert.assertTrue(store.stopImpersonation(adminSession.get("token").toString()));

            Map<String, Object> restored = store.loadSession(adminSession.get("token").toString());
            Assert.assertEquals("admin", restored.get("effectiveUsername"));
            Assert.assertEquals(Boolean.FALSE, restored.get("impersonating"));
        }
    }
}
