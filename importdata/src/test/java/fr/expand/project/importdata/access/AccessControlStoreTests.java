package fr.expand.project.importdata.access;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class AccessControlStoreTests {

    private static final String ACCESS_DB_PATH = "ACCESS_DB_PATH";
    private static final String ACCESS_BOOTSTRAP_MODE = "ACCESS_BOOTSTRAP_MODE";
    private static final String ACCESS_BOOTSTRAP_ADMIN_PASSWORD = "ACCESS_BOOTSTRAP_ADMIN_PASSWORD";

    private Path tempDirectory;

    @After
    public void tearDown() throws IOException {
        System.clearProperty(ACCESS_DB_PATH);
        System.clearProperty(ACCESS_BOOTSTRAP_MODE);
        System.clearProperty(ACCESS_BOOTSTRAP_ADMIN_PASSWORD);

        if (tempDirectory != null && Files.exists(tempDirectory)) {
            try (Stream<Path> walk = Files.walk(tempDirectory)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
    }

    @Test
    public void ensureBootstrapAdmin_shouldRequireExplicitPasswordInProduction() throws Exception {
        configureStore("production", null);

        try (AccessControlStore store = new AccessControlStore()) {
            Assert.assertNull(store.loadUser("admin"));

            Map<String, Object> status = store.getBootstrapStatus();
            Assert.assertEquals("production", status.get("mode"));
            Assert.assertEquals(Boolean.TRUE, status.get("firstStart"));
            Assert.assertEquals(Boolean.FALSE, status.get("loginReady"));
            Assert.assertEquals(Boolean.TRUE, status.get("requiresSetup"));
        }
    }

    @Test
    public void ensureBootstrapAdmin_shouldCreateAdminFromConfiguredPasswordInDevelopment() throws Exception {
        configureStore("development", "local-dev-bootstrap-secret");

        try (AccessControlStore store = new AccessControlStore()) {
            Assert.assertNotNull(store.loadUser("admin"));
            Assert.assertNotNull(store.authenticate("admin", "local-dev-bootstrap-secret"));

            Map<String, Object> status = store.getBootstrapStatus();
            Assert.assertEquals("development", status.get("mode"));
            Assert.assertEquals(Boolean.TRUE, status.get("firstStart"));
            Assert.assertEquals(Boolean.TRUE, status.get("loginReady"));
            Assert.assertEquals(Boolean.TRUE, status.get("passwordConfigured"));
            Assert.assertEquals(Boolean.TRUE, status.get("passwordChangeRecommended"));
            Assert.assertEquals(Boolean.FALSE, status.get("passwordChangeRequired"));
        }
    }

    @Test
    public void ensureBootstrapAdmin_shouldPreserveRotatedAdminPassword() throws Exception {
        configureStore("development", "local-dev-bootstrap-secret");

        try (AccessControlStore store = new AccessControlStore()) {
            store.upsertUser("admin", "Administrateur", true, true, true, "AdminPassword#2");
        }

        try (AccessControlStore store = new AccessControlStore()) {
            Assert.assertNull(store.authenticate("admin", "local-dev-bootstrap-secret"));
            Assert.assertNotNull(store.authenticate("admin", "AdminPassword#2"));

            Map<String, Object> status = store.getBootstrapStatus();
            Assert.assertEquals(Boolean.TRUE, status.get("loginReady"));
            Assert.assertEquals(Boolean.FALSE, status.get("passwordChangeRecommended"));
        }
    }

    private void configureStore(String mode, String bootstrapPassword) throws IOException {
        tempDirectory = Files.createTempDirectory("expand-access-tests");
        System.setProperty(ACCESS_DB_PATH, tempDirectory.resolve("access.sqlite").toString());
        System.setProperty(ACCESS_BOOTSTRAP_MODE, mode);
        if (bootstrapPassword == null) {
            System.clearProperty(ACCESS_BOOTSTRAP_ADMIN_PASSWORD);
        } else {
            System.setProperty(ACCESS_BOOTSTRAP_ADMIN_PASSWORD, bootstrapPassword);
        }
    }
}
