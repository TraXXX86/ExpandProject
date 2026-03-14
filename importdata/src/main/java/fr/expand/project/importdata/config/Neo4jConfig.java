package fr.expand.project.importdata.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.neo4j.driver.AuthToken;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

/**
 * Centralized Neo4j connection settings and auth resolution.
 */
public final class Neo4jConfig {

    public static final String DEFAULT_BOLT_URI = "bolt://localhost:7687";
    public static final String DEFAULT_JDBC_URI = "jdbc:neo4j:http://localhost:7474";
    public static final String DEFAULT_USER = "neo4j";
    public static final String DEFAULT_PASSWORD = "expand123456";

    private final String boltUri;
    private final String jdbcUri;
    private final String username;
    private final String password;
    private final boolean authDisabled;

    public Neo4jConfig(String boltUri, String jdbcUri, String username, String password, boolean authDisabled) {
        this.boltUri = Objects.requireNonNull(boltUri, "boltUri");
        this.jdbcUri = Objects.requireNonNull(jdbcUri, "jdbcUri");
        this.username = Objects.requireNonNull(username, "username");
        this.password = Objects.requireNonNull(password, "password");
        this.authDisabled = authDisabled;
    }

    public static Neo4jConfig fromSystem() {
        return from(System.getProperties(), System.getenv());
    }

    public static Neo4jConfig from(Properties systemProperties, Map<String, String> environmentValues) {
        String boltUri = readSetting(systemProperties, environmentValues, DEFAULT_BOLT_URI, "NEO4J_BOLT_URI", "NEO4J_URI");
        String jdbcUri = readSetting(systemProperties, environmentValues, DEFAULT_JDBC_URI, "NEO4J_HTTP_URI", "NEO4J_JDBC_URI");
        AuthSettings authSettings = resolveAuth(systemProperties, environmentValues);
        return new Neo4jConfig(
            boltUri,
            jdbcUri,
            authSettings.username(),
            authSettings.password(),
            authSettings.disabled()
        );
    }

    public Driver createDriver() {
        return GraphDatabase.driver(boltUri, createAuthToken());
    }

    public Connection createJdbcConnection() throws SQLException {
        if (authDisabled) {
            return DriverManager.getConnection(jdbcUri);
        }
        return DriverManager.getConnection(jdbcUri, username, password);
    }

    public AuthToken createAuthToken() {
        if (authDisabled) {
            return AuthTokens.none();
        }
        return AuthTokens.basic(username, password);
    }

    public String getBoltUri() {
        return boltUri;
    }

    public String getJdbcUri() {
        return jdbcUri;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isAuthDisabled() {
        return authDisabled;
    }

    private static AuthSettings resolveAuth(Properties systemProperties, Map<String, String> environmentValues) {
        String authValue = readSetting(systemProperties, environmentValues, null, "NEO4J_AUTH");
        if (authValue != null) {
            if ("none".equalsIgnoreCase(authValue)) {
                return new AuthSettings(DEFAULT_USER, DEFAULT_PASSWORD, true);
            }
            int separatorIndex = authValue.indexOf('/');
            if (separatorIndex > 0 && separatorIndex < authValue.length() - 1) {
                String username = normalize(authValue.substring(0, separatorIndex));
                String password = normalize(authValue.substring(separatorIndex + 1));
                if (username != null && password != null) {
                    return new AuthSettings(username, password, false);
                }
            }
        }

        String username = readSetting(systemProperties, environmentValues, DEFAULT_USER, "NEO4J_USER");
        String password = readSetting(systemProperties, environmentValues, DEFAULT_PASSWORD, "NEO4J_PASSWORD");
        return new AuthSettings(username, password, false);
    }

    private static String readSetting(
        Properties systemProperties,
        Map<String, String> environmentValues,
        String defaultValue,
        String... keys
    ) {
        for (String key : keys) {
            String propertyValue = normalize(systemProperties == null ? null : systemProperties.getProperty(key));
            if (propertyValue != null) {
                return propertyValue;
            }
            String envValue = normalize(environmentValues == null ? null : environmentValues.get(key));
            if (envValue != null) {
                return envValue;
            }
        }
        return defaultValue;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record AuthSettings(String username, String password, boolean disabled) {
    }
}
