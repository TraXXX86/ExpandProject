package fr.expand.project.importdata.access;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class AccessControlStoreTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private Path database() throws Exception {
        return temp.newFolder().toPath().resolve("access.sqlite");
    }

    private Connection connect(Path path) throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + path);
    }

    private String scalar(Path path, String sql) throws Exception {
        try (Connection connection = connect(path);
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }

    @Test
    public void bootstrapRequiresExplicitSecretOnlyForEmptyDatabase() throws Exception {
        Path path = database();
        IllegalStateException missing =
                assertThrows(IllegalStateException.class, () -> new AccessControlStore(path, null));
        assertTrue(missing.getMessage().contains("EXPAND_ADMIN_PASSWORD"));
        assertEquals("0", scalar(path, "SELECT COUNT(*) FROM users"));
        try (AccessControlStore store = new AccessControlStore(path, "initial-secret")) {
            assertNull(store.authenticate("admin", "admin"));
            assertNotNull(store.authenticate("admin", "initial-secret"));
            assertEquals("1", scalar(path, "SELECT password_version FROM users"));
            assertEquals("600000", scalar(path, "SELECT password_iterations FROM users"));
            store.upsertUser("admin", "Renamed", false, false, false);
        }
        try (AccessControlStore reopened = new AccessControlStore(path, null)) {
            assertFalse((Boolean) reopened.loadUser("admin").get("platformAdmin"));
            reopened.ensureBootstrapAdmin();
            assertFalse((Boolean) reopened.loadUser("admin").get("platformAdmin"));
            assertNotNull(reopened.authenticate("admin", "initial-secret"));
        }
    }

    @Test
    public void existingDatabaseWithoutAdminDoesNotRecreateIt() throws Exception {
        Path path = database();
        try (AccessControlStore store = new AccessControlStore(path, "initial-secret")) {
            store.upsertUser("operator", "Operator", true, true, true, "operator-secret");
        }
        try (Connection connection = connect(path);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM users WHERE username='admin'");
        }
        try (AccessControlStore store = new AccessControlStore(path, null)) {
            assertNull(store.loadUser("admin"));
            assertNotNull(store.authenticate("operator", "operator-secret"));
            store.listUsers();
            assertEquals("1", scalar(path, "SELECT COUNT(*) FROM users"));
        }
    }

    @Test
    public void newUsersNeedPasswordsAndOrdinaryUpdatesPreserveCredentialsAndSessions()
            throws Exception {
        Path path = database();
        try (AccessControlStore store = new AccessControlStore(path, "initial-secret")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> store.upsertUser("alice", "Alice", true, false, false));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> store.upsertUser("alice", "Alice", true, false, false, "  "));
            assertNull(store.loadUser("alice"));
            store.upsertUser("alice", "Alice", true, false, false, "alice-secret");
            store.upsertUser("bob", "Bob", true, false, false, "alice-secret");
            assertEquals(
                    "2",
                    scalar(
                            path,
                            "SELECT COUNT(DISTINCT password_salt) FROM users WHERE username IN"
                                + " ('alice','bob')"));
            String before = scalar(path, "SELECT password_hash FROM users WHERE username='alice'");
            String token = (String) store.createSession("alice").get("token");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> store.upsertUser("alice", "Alice", true, false, false, "p".repeat(4097)));
            assertNotNull(store.loadSession(token));
            assertEquals(
                    before, scalar(path, "SELECT password_hash FROM users WHERE username='alice'"));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            store.upsertUser(
                                    "u".repeat(129), "Too long", true, false, false, "password"));
            store.upsertUser("alice", "Alice renamed", true, true, false);
            assertEquals(
                    before, scalar(path, "SELECT password_hash FROM users WHERE username='alice'"));
            assertNotNull(store.loadSession(token));
            assertNotNull(store.authenticate("alice", "alice-secret"));
            assertNull(store.authenticate("alice", "alice"));
            assertFalse(store.loadUser("alice").containsKey("passwordHash"));
            assertFalse(store.authenticate("alice", "alice-secret").containsKey("passwordSalt"));
        }
    }

    @Test
    public void resetRevokesActorAndEffectiveSessionsAndPreservesOthers() throws Exception {
        try (AccessControlStore store = new AccessControlStore(database(), "initial-secret")) {
            store.upsertUser("alice", "Alice", true, false, false, "alice-secret");
            store.upsertUser("bob", "Bob", true, false, false, "bob-secret");
            String alice = (String) store.createSession("alice").get("token");
            String impersonated = (String) store.createSession("admin").get("token");
            String bob = (String) store.createSession("bob").get("token");
            assertTrue(store.impersonateSession(impersonated, "alice"));
            store.upsertUser("alice", "Alice", true, false, false, "replacement-secret");
            assertNull(store.loadSession(alice));
            assertNull(store.loadSession(impersonated));
            assertNotNull(store.loadSession(bob));
            assertNull(store.authenticate("alice", "alice-secret"));
            assertNotNull(store.authenticate("alice", "replacement-secret"));
        }
    }

    @Test
    public void concurrentLoginAndResetCannotLeaveAnOldPasswordSession() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try (AccessControlStore store = new AccessControlStore(database(), "initial-secret")) {
            for (int attempt = 0; attempt < 4; attempt++) {
                store.upsertUser("alice", "Alice", true, false, false, "old-secret");
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                Future<Map<String, Object>> login =
                        workers.submit(
                                () -> {
                                    ready.countDown();
                                    assertTrue(start.await(5, TimeUnit.SECONDS));
                                    return store.authenticateAndCreateSession(
                                            "alice", "old-secret");
                                });
                Future<?> reset =
                        workers.submit(
                                () -> {
                                    ready.countDown();
                                    assertTrue(start.await(5, TimeUnit.SECONDS));
                                    store.upsertUser(
                                            "alice", "Alice", true, false, false, "new-secret");
                                    return null;
                                });
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                Map<String, Object> session = login.get(10, TimeUnit.SECONDS);
                reset.get(10, TimeUnit.SECONDS);
                if (session != null) assertNull(store.loadSession((String) session.get("token")));
                assertNull(store.authenticateAndCreateSession("alice", "old-secret"));
                assertNotNull(store.authenticateAndCreateSession("alice", "new-secret"));
            }
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    public void opaqueTokensAreHashedAndImpersonationExpiryLogoutStillWork() throws Exception {
        Path path = database();
        try (AccessControlStore store = new AccessControlStore(path, "initial-secret")) {
            store.upsertUser("alice", "Alice", true, false, false, "alice-secret");
            String token = (String) store.createSession("admin").get("token");
            String persisted = scalar(path, "SELECT token FROM sessions");
            assertNotEquals(token, persisted);
            assertNull(store.loadSession(persisted));
            assertEquals(token, store.loadSession(token).get("token"));
            assertTrue(store.impersonateSession(token, "alice"));
            assertEquals("alice", store.loadSession(token).get("effectiveUsername"));
            assertTrue(store.stopImpersonation(token));
            assertEquals("admin", store.loadSession(token).get("effectiveUsername"));
            assertTrue(store.deleteSession(token));
            assertNull(store.loadSession(token));
            String expired = (String) store.createSession("alice").get("token");
            try (Connection connection = connect(path);
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE sessions SET expires_at=0");
            }
            assertNull(store.loadSession(expired));
            assertFalse(store.impersonateSession(expired, "admin"));
        }
    }

    @Test
    public void legacySchemaPasswordAndSessionsMigrateWithoutBootstrap() throws Exception {
        Path path = database();
        Class.forName("org.sqlite.JDBC");
        byte[] salt = new byte[16];
        java.util.Arrays.fill(salt, (byte) 42);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        String legacyHash =
                Base64.getEncoder()
                        .encodeToString(
                                digest.digest("legacy-secret".getBytes(StandardCharsets.UTF_8)));
        try (Connection connection = connect(path);
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE users(username TEXT PRIMARY KEY, display_name TEXT, password_hash"
                        + " TEXT, password_salt TEXT, portal_user INTEGER, portal_model_admin"
                        + " INTEGER, platform_admin INTEGER, updated_at INTEGER)");
            try (PreparedStatement insert =
                    connection.prepareStatement(
                            "INSERT INTO users VALUES('legacy','Legacy',?,?,1,0,0,1)")) {
                insert.setString(1, legacyHash);
                insert.setString(2, Base64.getEncoder().encodeToString(salt));
                insert.executeUpdate();
            }
            statement.execute(
                    "CREATE TABLE sessions(token TEXT PRIMARY KEY, actor_username TEXT, "
                            + "effective_username TEXT, created_at INTEGER, expires_at INTEGER)");
            statement.execute(
                    "INSERT INTO sessions"
                        + " VALUES('old-opaque-token','legacy','legacy',1,4102444800)");
        }
        try (AccessControlStore store = new AccessControlStore(path, null)) {
            assertNull(store.loadUser("admin"));
            assertNotNull(store.loadSession("old-opaque-token"));
            assertNotEquals("old-opaque-token", scalar(path, "SELECT token FROM sessions"));
            assertNull(store.authenticate("legacy", "wrong"));
            assertEquals("0", scalar(path, "SELECT password_version FROM users"));
            Map<String, Object> migratedSession =
                    store.authenticateAndCreateSession("legacy", "legacy-secret");
            assertNotNull(migratedSession);
            assertNotNull(store.loadSession((String) migratedSession.get("token")));
            assertEquals("1", scalar(path, "SELECT password_version FROM users"));
            assertNotEquals(legacyHash, scalar(path, "SELECT password_hash FROM users"));
            assertNotNull(store.loadSession("old-opaque-token"));
            assertNotNull(store.authenticate("legacy", "legacy-secret"));
        }
        try (AccessControlStore reopened = new AccessControlStore(path, null)) {
            assertNotNull(reopened.loadSession("old-opaque-token"));
        }
    }

    @Test
    public void malformedAndUnknownPasswordFormatsFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash("p".repeat(4097)));
        assertFalse(PasswordHasher.verify("p".repeat(4097), "unused", "unused", 1, 600000));
        assertFalse(PasswordHasher.verify("password", "%%%", "%%%", 0, 0));
        assertFalse(
                PasswordHasher.verify(
                        "password",
                        Base64.getEncoder().encodeToString(new byte[16]),
                        Base64.getEncoder().encodeToString(new byte[32]),
                        99,
                        600000));
        assertFalse(
                PasswordHasher.verify(
                        "password",
                        Base64.getEncoder().encodeToString(new byte[16]),
                        Base64.getEncoder().encodeToString(new byte[32]),
                        1,
                        Integer.MAX_VALUE));
    }
}
