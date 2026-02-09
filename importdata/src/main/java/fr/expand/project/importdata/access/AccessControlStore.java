package fr.expand.project.importdata.access;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Store for users, permissions and auth sessions persisted in SQLite.
 */
public class AccessControlStore implements AutoCloseable {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_DEFAULT_PASSWORD = "admin";
    private static final long SESSION_TTL_SECONDS = 12 * 60 * 60;

    private final String sqlitePath;
    private final String jdbcUrl;

    public AccessControlStore() {
        this.sqlitePath = resolveSqlitePath();
        this.jdbcUrl = "jdbc:sqlite:" + sqlitePath;
        initialize();
    }

    public void ensureBootstrapAdmin() {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<String, Object> admin = loadUserInternal(connection, ADMIN_USERNAME);
                if (admin == null) {
                    upsertUserInternal(
                        connection,
                        ADMIN_USERNAME,
                        "Administrateur",
                        true,
                        true,
                        true,
                        ADMIN_DEFAULT_PASSWORD
                    );
                } else {
                    boolean hasPassword = getString(admin.get("passwordHash")) != null
                        && !getString(admin.get("passwordHash")).isBlank();
                    String password = hasPassword ? null : ADMIN_DEFAULT_PASSWORD;
                    upsertUserInternal(
                        connection,
                        ADMIN_USERNAME,
                        getString(admin.get("displayName")),
                        true,
                        true,
                        true,
                        password
                    );
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to bootstrap admin user", e);
        }
    }

    public Map<String, Object> authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null) {
            return null;
        }
        ensureBootstrapAdmin();
        try (Connection connection = openConnection()) {
            Map<String, Object> user = loadUserInternal(connection, username.trim());
            if (user == null) {
                return null;
            }
            String storedHash = getString(user.get("passwordHash"));
            String storedSalt = getString(user.get("passwordSalt"));
            if (storedHash == null || storedSalt == null || storedHash.isBlank() || storedSalt.isBlank()) {
                return null;
            }
            String computedHash = hashPassword(password, storedSalt);
            if (!storedHash.equals(computedHash)) {
                return null;
            }
            return sanitizeUser(user);
        } catch (Exception e) {
            throw new RuntimeException("Unable to authenticate user", e);
        }
    }

    public Map<String, Object> createSession(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        ensureBootstrapAdmin();
        String normalizedUsername = username.trim();
        long now = Instant.now().getEpochSecond();
        long expiresAt = now + SESSION_TTL_SECONDS;
        String token = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                if (loadUserInternal(connection, normalizedUsername) == null) {
                    connection.rollback();
                    return null;
                }

                try (PreparedStatement clean = connection.prepareStatement(
                    "DELETE FROM sessions WHERE expires_at <= ?"
                )) {
                    clean.setLong(1, now);
                    clean.executeUpdate();
                }

                try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO sessions(token, actor_username, effective_username, created_at, expires_at) "
                        + "VALUES(?, ?, ?, ?, ?)"
                )) {
                    statement.setString(1, token);
                    statement.setString(2, normalizedUsername);
                    statement.setString(3, normalizedUsername);
                    statement.setLong(4, now);
                    statement.setLong(5, expiresAt);
                    statement.executeUpdate();
                }

                connection.commit();
                return loadSessionInternal(connection, token);
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to create session", e);
        }
    }

    public Map<String, Object> loadSession(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        ensureBootstrapAdmin();
        try (Connection connection = openConnection()) {
            return loadSessionInternal(connection, token.trim());
        } catch (Exception e) {
            throw new RuntimeException("Unable to load session", e);
        }
    }

    public boolean deleteSession(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try (Connection connection = openConnection();
            PreparedStatement statement = connection.prepareStatement("DELETE FROM sessions WHERE token = ?")) {
            statement.setString(1, token.trim());
            return statement.executeUpdate() > 0;
        } catch (Exception e) {
            throw new RuntimeException("Unable to delete session", e);
        }
    }

    public boolean impersonateSession(String token, String targetUsername) {
        if (token == null || token.isBlank() || targetUsername == null || targetUsername.isBlank()) {
            return false;
        }
        ensureBootstrapAdmin();
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<String, Object> session = loadSessionInternal(connection, token.trim());
                if (session == null) {
                    connection.rollback();
                    return false;
                }
                String actorUsername = getString(session.get("actorUsername"));
                Map<String, Object> actor = loadUserInternal(connection, actorUsername);
                Map<String, Object> target = loadUserInternal(connection, targetUsername.trim());
                if (actor == null || target == null || !getBoolean(actor.get("platformAdmin"))) {
                    connection.rollback();
                    return false;
                }
                try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE sessions SET effective_username = ?, expires_at = ? WHERE token = ?"
                )) {
                    statement.setString(1, targetUsername.trim());
                    statement.setLong(2, Instant.now().getEpochSecond() + SESSION_TTL_SECONDS);
                    statement.setString(3, token.trim());
                    statement.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to impersonate user", e);
        }
    }

    public boolean stopImpersonation(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        ensureBootstrapAdmin();
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<String, Object> session = loadSessionInternal(connection, token.trim());
                if (session == null) {
                    connection.rollback();
                    return false;
                }
                String actorUsername = getString(session.get("actorUsername"));
                try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE sessions SET effective_username = ?, expires_at = ? WHERE token = ?"
                )) {
                    statement.setString(1, actorUsername);
                    statement.setLong(2, Instant.now().getEpochSecond() + SESSION_TTL_SECONDS);
                    statement.setString(3, token.trim());
                    statement.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to stop impersonation", e);
        }
    }

    public List<Map<String, Object>> listUsers() {
        ensureBootstrapAdmin();
        try (Connection connection = openConnection();
            PreparedStatement statement = connection.prepareStatement(
                "SELECT username, display_name, portal_user, portal_model_admin, platform_admin "
                    + "FROM users ORDER BY lower(display_name), lower(username)"
            );
            ResultSet rs = statement.executeQuery()) {
            List<Map<String, Object>> users = new ArrayList<>();
            while (rs.next()) {
                users.add(sanitizeUser(fromUserResultSet(rs, false)));
            }
            return users;
        } catch (Exception e) {
            throw new RuntimeException("Unable to list users", e);
        }
    }

    public Map<String, Object> loadUser(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        ensureBootstrapAdmin();
        try (Connection connection = openConnection()) {
            Map<String, Object> user = loadUserInternal(connection, username.trim());
            return sanitizeUser(user);
        } catch (Exception e) {
            throw new RuntimeException("Unable to load user", e);
        }
    }

    public Map<String, Object> loadUserAccess(String username) {
        Map<String, Object> user = loadUser(username);
        if (user == null) {
            return null;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("user", user);
        payload.put("permissions", listModelPermissions(username));
        return payload;
    }

    public List<Map<String, Object>> listModelPermissions(String username) {
        if (username == null || username.isBlank()) {
            return List.of();
        }
        ensureBootstrapAdmin();
        try (Connection connection = openConnection();
            PreparedStatement statement = connection.prepareStatement(
                "SELECT model_key, visible, can_read, can_create, can_update, can_delete "
                    + "FROM model_permissions WHERE username = ? ORDER BY lower(model_key)"
            )) {
            statement.setString(1, username.trim());
            try (ResultSet rs = statement.executeQuery()) {
                List<Map<String, Object>> permissions = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("modelKey", rs.getString("model_key"));
                    row.put("modelName", "");
                    row.put("modelVersion", "");
                    row.put("visible", rs.getInt("visible") == 1);
                    row.put("canRead", rs.getInt("can_read") == 1);
                    row.put("canCreate", rs.getInt("can_create") == 1);
                    row.put("canUpdate", rs.getInt("can_update") == 1);
                    row.put("canDelete", rs.getInt("can_delete") == 1);
                    permissions.add(row);
                }
                return permissions;
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to list model permissions", e);
        }
    }

    public void upsertUser(String username, String displayName, boolean portalUser, boolean portalModelAdmin,
        boolean platformAdmin) {
        upsertUser(username, displayName, portalUser, portalModelAdmin, platformAdmin, null);
    }

    public void upsertUser(String username, String displayName, boolean portalUser, boolean portalModelAdmin,
        boolean platformAdmin, String plainPassword) {
        if (username == null || username.isBlank()) {
            return;
        }
        String normalizedUsername = username.trim();
        boolean normalizedPortalUser = portalUser;
        boolean normalizedPortalModelAdmin = portalModelAdmin;
        boolean normalizedPlatformAdmin = platformAdmin;
        if (ADMIN_USERNAME.equalsIgnoreCase(normalizedUsername)) {
            normalizedPortalUser = true;
            normalizedPortalModelAdmin = true;
            normalizedPlatformAdmin = true;
        }

        ensureBootstrapAdmin();
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                upsertUserInternal(
                    connection,
                    normalizedUsername,
                    displayName,
                    normalizedPortalUser,
                    normalizedPortalModelAdmin,
                    normalizedPlatformAdmin,
                    plainPassword
                );
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to upsert user", e);
        }
    }

    public boolean deleteUser(String username) {
        if (username == null || username.isBlank() || ADMIN_USERNAME.equalsIgnoreCase(username.trim())) {
            return false;
        }
        ensureBootstrapAdmin();
        try (Connection connection = openConnection();
            PreparedStatement statement = connection.prepareStatement("DELETE FROM users WHERE username = ?")) {
            statement.setString(1, username.trim());
            return statement.executeUpdate() > 0;
        } catch (Exception e) {
            throw new RuntimeException("Unable to delete user", e);
        }
    }

    public void replaceModelPermissions(String username, List<Map<String, Object>> permissions) {
        if (username == null || username.isBlank()) {
            return;
        }

        List<Map<String, Object>> normalized = new ArrayList<>();
        if (permissions != null) {
            for (Map<String, Object> entry : permissions) {
                if (entry == null) {
                    continue;
                }
                String modelKey = getString(entry.get("modelKey"));
                if (modelKey == null || modelKey.isBlank()) {
                    continue;
                }
                Map<String, Object> row = new HashMap<>();
                row.put("modelKey", modelKey.trim());
                row.put("visible", getBoolean(entry.get("visible")));
                row.put("canRead", getBoolean(entry.get("canRead")));
                row.put("canCreate", getBoolean(entry.get("canCreate")));
                row.put("canUpdate", getBoolean(entry.get("canUpdate")));
                row.put("canDelete", getBoolean(entry.get("canDelete")));
                normalized.add(row);
            }
        }

        ensureBootstrapAdmin();
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM model_permissions WHERE username = ?"
                )) {
                    delete.setString(1, username.trim());
                    delete.executeUpdate();
                }

                if (!normalized.isEmpty()) {
                    try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO model_permissions("
                            + "username, model_key, visible, can_read, can_create, can_update, can_delete, updated_at"
                            + ") VALUES(?, ?, ?, ?, ?, ?, ?, ?)"
                    )) {
                        long now = Instant.now().getEpochSecond();
                        for (Map<String, Object> permission : normalized) {
                            insert.setString(1, username.trim());
                            insert.setString(2, getString(permission.get("modelKey")));
                            insert.setInt(3, getBoolean(permission.get("visible")) ? 1 : 0);
                            insert.setInt(4, getBoolean(permission.get("canRead")) ? 1 : 0);
                            insert.setInt(5, getBoolean(permission.get("canCreate")) ? 1 : 0);
                            insert.setInt(6, getBoolean(permission.get("canUpdate")) ? 1 : 0);
                            insert.setInt(7, getBoolean(permission.get("canDelete")) ? 1 : 0);
                            insert.setLong(8, now);
                            insert.addBatch();
                        }
                        insert.executeBatch();
                    }
                }

                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to replace model permissions", e);
        }
    }

    @Override
    public void close() {
        // No persistent resources to close.
    }

    private void initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            Path path = Paths.get(sqlitePath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute(
                    "CREATE TABLE IF NOT EXISTS users ("
                        + "username TEXT PRIMARY KEY, "
                        + "display_name TEXT NOT NULL DEFAULT '', "
                        + "password_hash TEXT NOT NULL DEFAULT '', "
                        + "password_salt TEXT NOT NULL DEFAULT '', "
                        + "portal_user INTEGER NOT NULL DEFAULT 0, "
                        + "portal_model_admin INTEGER NOT NULL DEFAULT 0, "
                        + "platform_admin INTEGER NOT NULL DEFAULT 0, "
                        + "updated_at INTEGER NOT NULL"
                        + ")"
                );
                statement.execute(
                    "CREATE TABLE IF NOT EXISTS model_permissions ("
                        + "username TEXT NOT NULL, "
                        + "model_key TEXT NOT NULL, "
                        + "visible INTEGER NOT NULL DEFAULT 0, "
                        + "can_read INTEGER NOT NULL DEFAULT 0, "
                        + "can_create INTEGER NOT NULL DEFAULT 0, "
                        + "can_update INTEGER NOT NULL DEFAULT 0, "
                        + "can_delete INTEGER NOT NULL DEFAULT 0, "
                        + "updated_at INTEGER NOT NULL, "
                        + "PRIMARY KEY (username, model_key), "
                        + "FOREIGN KEY(username) REFERENCES users(username) ON DELETE CASCADE"
                        + ")"
                );
                statement.execute(
                    "CREATE TABLE IF NOT EXISTS sessions ("
                        + "token TEXT PRIMARY KEY, "
                        + "actor_username TEXT NOT NULL, "
                        + "effective_username TEXT NOT NULL, "
                        + "created_at INTEGER NOT NULL, "
                        + "expires_at INTEGER NOT NULL, "
                        + "FOREIGN KEY(actor_username) REFERENCES users(username) ON DELETE CASCADE, "
                        + "FOREIGN KEY(effective_username) REFERENCES users(username) ON DELETE CASCADE"
                        + ")"
                );
                statement.execute("CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON sessions(expires_at)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_permissions_username ON model_permissions(username)");
            }
            ensureBootstrapAdmin();
        } catch (Exception e) {
            throw new RuntimeException("Unable to initialize SQLite access store", e);
        }
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    private Map<String, Object> loadUserInternal(Connection connection, String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT username, display_name, password_hash, password_salt, portal_user, portal_model_admin, platform_admin "
                + "FROM users WHERE username = ?"
        )) {
            statement.setString(1, username);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return fromUserResultSet(rs, true);
            }
        }
    }

    private Map<String, Object> fromUserResultSet(ResultSet rs, boolean includePassword) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("username", rs.getString("username"));
        row.put("displayName", rs.getString("display_name") == null ? "" : rs.getString("display_name"));
        row.put("portalUser", rs.getInt("portal_user") == 1);
        row.put("portalModelAdmin", rs.getInt("portal_model_admin") == 1);
        row.put("platformAdmin", rs.getInt("platform_admin") == 1);
        if (includePassword) {
            row.put("passwordHash", rs.getString("password_hash") == null ? "" : rs.getString("password_hash"));
            row.put("passwordSalt", rs.getString("password_salt") == null ? "" : rs.getString("password_salt"));
        }
        return row;
    }

    private Map<String, Object> sanitizeUser(Map<String, Object> user) {
        if (user == null) {
            return null;
        }
        Map<String, Object> copy = new HashMap<>();
        copy.put("username", getString(user.get("username")));
        copy.put("displayName", getString(user.get("displayName")) == null ? "" : getString(user.get("displayName")));
        copy.put("portalUser", getBoolean(user.get("portalUser")));
        copy.put("portalModelAdmin", getBoolean(user.get("portalModelAdmin")));
        copy.put("platformAdmin", getBoolean(user.get("platformAdmin")));
        return copy;
    }

    private void upsertUserInternal(Connection connection, String username, String displayName, boolean portalUser,
        boolean portalModelAdmin, boolean platformAdmin, String plainPassword) throws Exception {
        Map<String, Object> current = loadUserInternal(connection, username);
        String finalDisplayName = (displayName == null || displayName.isBlank()) ? username : displayName.trim();

        String finalSalt;
        String finalHash;

        if (plainPassword != null && !plainPassword.isBlank()) {
            finalSalt = generateSalt();
            finalHash = hashPassword(plainPassword, finalSalt);
        } else if (current != null && getString(current.get("passwordHash")) != null
            && !getString(current.get("passwordHash")).isBlank()) {
            finalSalt = getString(current.get("passwordSalt"));
            finalHash = getString(current.get("passwordHash"));
        } else {
            String defaultPassword = username;
            finalSalt = generateSalt();
            finalHash = hashPassword(defaultPassword, finalSalt);
        }

        long now = Instant.now().getEpochSecond();

        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO users(username, display_name, password_hash, password_salt, portal_user, portal_model_admin, platform_admin, updated_at) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(username) DO UPDATE SET "
                + "display_name=excluded.display_name, "
                + "password_hash=excluded.password_hash, "
                + "password_salt=excluded.password_salt, "
                + "portal_user=excluded.portal_user, "
                + "portal_model_admin=excluded.portal_model_admin, "
                + "platform_admin=excluded.platform_admin, "
                + "updated_at=excluded.updated_at"
        )) {
            statement.setString(1, username);
            statement.setString(2, finalDisplayName);
            statement.setString(3, finalHash);
            statement.setString(4, finalSalt);
            statement.setInt(5, portalUser ? 1 : 0);
            statement.setInt(6, portalModelAdmin ? 1 : 0);
            statement.setInt(7, platformAdmin ? 1 : 0);
            statement.setLong(8, now);
            statement.executeUpdate();
        }
    }

    private Map<String, Object> loadSessionInternal(Connection connection, String token) throws SQLException {
        long now = Instant.now().getEpochSecond();
        try (PreparedStatement expire = connection.prepareStatement("DELETE FROM sessions WHERE expires_at <= ?")) {
            expire.setLong(1, now);
            expire.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT token, actor_username, effective_username, created_at, expires_at "
                + "FROM sessions WHERE token = ?"
        )) {
            statement.setString(1, token);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> row = new HashMap<>();
                row.put("token", rs.getString("token"));
                row.put("actorUsername", rs.getString("actor_username"));
                row.put("effectiveUsername", rs.getString("effective_username"));
                row.put("createdAt", rs.getLong("created_at"));
                row.put("expiresAt", rs.getLong("expires_at"));
                row.put("impersonating", !rs.getString("actor_username").equals(rs.getString("effective_username")));
                return row;
            }
        }
    }

    private static String resolveSqlitePath() {
        String explicit = readSetting("ACCESS_DB_PATH", null, null);
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        return Paths.get(System.getProperty("user.home"), ".expandproject", "access.sqlite").toString();
    }

    private static String generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private static String hashPassword(String password, String saltBase64) throws Exception {
        byte[] salt = Base64.getDecoder().decode(saltBase64);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    private static String readSetting(String envKey, String fallbackEnvKey, String defaultValue) {
        String value = System.getProperty(envKey);
        if (value == null || value.isBlank()) {
            value = System.getenv(envKey);
        }
        if ((value == null || value.isBlank()) && fallbackEnvKey != null) {
            value = System.getProperty(fallbackEnvKey);
            if (value == null || value.isBlank()) {
                value = System.getenv(fallbackEnvKey);
            }
        }
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private static String getString(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private static boolean getBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
