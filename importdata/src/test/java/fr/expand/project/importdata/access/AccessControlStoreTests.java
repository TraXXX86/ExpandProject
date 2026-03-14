package fr.expand.project.importdata.access;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Comparator;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class AccessControlStoreTests {

    private String previousDbPath;
    private Path tempDirectory;
    private Path dbPath;

    @Before
    public void setUp() throws Exception {
        previousDbPath = System.getProperty("ACCESS_DB_PATH");
        tempDirectory = Files.createTempDirectory("access-control-store-tests");
        dbPath = tempDirectory.resolve("access.sqlite");
        System.setProperty("ACCESS_DB_PATH", dbPath.toString());
    }

    @After
    public void tearDown() throws Exception {
        if (previousDbPath == null) {
            System.clearProperty("ACCESS_DB_PATH");
        } else {
            System.setProperty("ACCESS_DB_PATH", previousDbPath);
        }

        if (tempDirectory != null && Files.exists(tempDirectory)) {
            Files.walk(tempDirectory)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // Best-effort cleanup for temporary SQLite files.
                    }
                });
        }
    }

    @Test
    public void loadSession_shouldDeleteExpiredSession() throws Exception {
        String token;
        try (AccessControlStore store = new AccessControlStore()) {
            store.upsertUser("alice", "Alice", true, false, false, "secret-1");
            Map<String, Object> session = store.createSession("alice");
            Assert.assertNotNull(session);
            token = session.get("token").toString();
        }

        try (Connection connection = openConnection();
            PreparedStatement statement = connection.prepareStatement(
                "UPDATE sessions SET expires_at = ? WHERE token = ?"
            )) {
            statement.setLong(1, 1L);
            statement.setString(2, token);
            statement.executeUpdate();
        }

        try (AccessControlStore store = new AccessControlStore()) {
            Assert.assertNull(store.loadSession(token));
        }

        Assert.assertEquals(0, countSessions());
    }

    @Test
    public void upsertUser_withNewPassword_shouldInvalidateExistingSessions() throws Exception {
        String token;
        try (AccessControlStore store = new AccessControlStore()) {
            store.upsertUser("bob", "Bob", true, false, false, "secret-1");
            Map<String, Object> session = store.createSession("bob");
            Assert.assertNotNull(session);
            token = session.get("token").toString();

            store.upsertUser("bob", "Bob", true, false, false, "secret-2");

            Assert.assertNull(store.loadSession(token));
            Assert.assertNull(store.authenticate("bob", "secret-1"));
            Assert.assertNotNull(store.authenticate("bob", "secret-2"));
        }

        Assert.assertEquals(0, countSessions());
    }

    private Connection openConnection() throws Exception {
        Class.forName("org.sqlite.JDBC");
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    private int countSessions() throws Exception {
        try (Connection connection = openConnection();
            PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM sessions");
            ResultSet rs = statement.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
