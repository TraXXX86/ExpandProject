package fr.expand.project.importdata.access;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AccessControlStoreTests {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static final String TEST_DB_PROPERTY = "ACCESS_DB_PATH";

    private Path sqlitePath;
    private String previousDbPath;

    @Before
    public void setUp() throws Exception {
        previousDbPath = System.getProperty(TEST_DB_PROPERTY);
        sqlitePath = temporaryFolder.newFolder("access-store").toPath().resolve("access.sqlite");
        System.setProperty(TEST_DB_PROPERTY, sqlitePath.toString());
        Class.forName("org.sqlite.JDBC");
    }

    @After
    public void tearDown() {
        if (previousDbPath == null) {
            System.clearProperty(TEST_DB_PROPERTY);
        } else {
            System.setProperty(TEST_DB_PROPERTY, previousDbPath);
        }
    }

    @Test
    public void newPasswordsUseVersionedPbkdf2Storage() throws Exception {
        try (AccessControlStore store = new AccessControlStore()) {
            store.upsertUser("alice", "Alice", true, false, false, "S3curePassword!");

            Map<String, Object> authenticated = store.authenticate("alice", "S3curePassword!");

            Assert.assertNotNull(authenticated);
            Assert.assertEquals("alice", authenticated.get("username"));
            Assert.assertNull(store.authenticate("alice", "wrong-password"));
        }

        PasswordRow stored = loadPasswordRow("alice");
        Assert.assertNotNull(stored);
        Assert.assertTrue(stored.passwordHash().startsWith("v2$pbkdf2-sha256$"));
        Assert.assertEquals("", stored.passwordSalt());
    }

    @Test
    public void legacyHashesAuthenticateAndUpgradeOnSuccessfulLogin() throws Exception {
        try (AccessControlStore store = new AccessControlStore()) {
            store.upsertUser("legacy-user", "Legacy User", true, false, false, "TempPassword!");
        }

        PasswordRow legacyRow = rewriteWithLegacyHash("legacy-user", "OldPassword!");

        try (AccessControlStore store = new AccessControlStore()) {
            Map<String, Object> authenticated = store.authenticate("legacy-user", "OldPassword!");

            Assert.assertNotNull(authenticated);
            Assert.assertEquals("legacy-user", authenticated.get("username"));
        }

        PasswordRow migrated = loadPasswordRow("legacy-user");
        Assert.assertNotNull(migrated);
        Assert.assertTrue(migrated.passwordHash().startsWith("v2$pbkdf2-sha256$"));
        Assert.assertEquals("", migrated.passwordSalt());
        Assert.assertNotEquals(legacyRow.passwordHash(), migrated.passwordHash());

        try (AccessControlStore store = new AccessControlStore()) {
            Assert.assertNotNull(store.authenticate("legacy-user", "OldPassword!"));
        }
    }

    @Test
    public void legacyHashesRemainUnchangedAfterFailedLogin() throws Exception {
        try (AccessControlStore store = new AccessControlStore()) {
            store.upsertUser("legacy-fail", "Legacy Fail", true, false, false, "TempPassword!");
        }

        PasswordRow legacyRow = rewriteWithLegacyHash("legacy-fail", "CorrectPassword!");

        try (AccessControlStore store = new AccessControlStore()) {
            Assert.assertNull(store.authenticate("legacy-fail", "WrongPassword!"));
        }

        Assert.assertEquals(legacyRow, loadPasswordRow("legacy-fail"));
    }

    private PasswordRow rewriteWithLegacyHash(String username, String password) throws Exception {
        byte[] salt = Arrays.copyOf(("legacy-" + username).getBytes(StandardCharsets.UTF_8), 16);
        String saltBase64 = Base64.getEncoder().encodeToString(salt);
        String hashBase64 = legacyHash(password, saltBase64);

        try (Connection connection = openConnection();
            PreparedStatement statement = connection.prepareStatement(
                "UPDATE users SET password_hash = ?, password_salt = ?, updated_at = ? WHERE username = ?"
            )) {
            statement.setString(1, hashBase64);
            statement.setString(2, saltBase64);
            statement.setLong(3, Instant.now().getEpochSecond());
            statement.setString(4, username);
            statement.executeUpdate();
        }

        return new PasswordRow(hashBase64, saltBase64);
    }

    private PasswordRow loadPasswordRow(String username) throws Exception {
        try (Connection connection = openConnection();
            PreparedStatement statement = connection.prepareStatement(
                "SELECT password_hash, password_salt FROM users WHERE username = ?"
            )) {
            statement.setString(1, username);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new PasswordRow(
                    rs.getString("password_hash") == null ? "" : rs.getString("password_hash"),
                    rs.getString("password_salt") == null ? "" : rs.getString("password_salt")
                );
            }
        }
    }

    private Connection openConnection() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + sqlitePath);
    }

    private static String legacyHash(String password, String saltBase64) throws Exception {
        byte[] salt = Base64.getDecoder().decode(saltBase64);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    private record PasswordRow(String passwordHash, String passwordSalt) {
    }
}
