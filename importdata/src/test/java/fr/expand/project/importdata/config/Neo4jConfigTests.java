package fr.expand.project.importdata.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

public class Neo4jConfigTests {

    @Test
    public void from_shouldUseDefaultsWhenNoSettingsProvided() {
        Neo4jConfig config = Neo4jConfig.from(new Properties(), Map.of());

        Assert.assertEquals(Neo4jConfig.DEFAULT_BOLT_URI, config.getBoltUri());
        Assert.assertEquals(Neo4jConfig.DEFAULT_JDBC_URI, config.getJdbcUri());
        Assert.assertEquals(Neo4jConfig.DEFAULT_USER, config.getUsername());
        Assert.assertEquals(Neo4jConfig.DEFAULT_PASSWORD, config.getPassword());
        Assert.assertFalse(config.isAuthDisabled());
    }

    @Test
    public void from_shouldResolvePrimaryAndFallbackKeys() {
        Properties properties = new Properties();
        properties.setProperty("NEO4J_URI", "bolt://property-fallback:17687");

        Map<String, String> environment = new HashMap<>();
        environment.put("NEO4J_JDBC_URI", "jdbc:neo4j:http://env-fallback:17474");

        Neo4jConfig config = Neo4jConfig.from(properties, environment);

        Assert.assertEquals("bolt://property-fallback:17687", config.getBoltUri());
        Assert.assertEquals("jdbc:neo4j:http://env-fallback:17474", config.getJdbcUri());
    }

    @Test
    public void from_shouldPreferExplicitAuthOverSeparateCredentials() {
        Properties properties = new Properties();
        properties.setProperty("NEO4J_AUTH", "alice/secret");
        properties.setProperty("NEO4J_USER", "ignored-user");
        properties.setProperty("NEO4J_PASSWORD", "ignored-password");

        Neo4jConfig config = Neo4jConfig.from(properties, Map.of());

        Assert.assertEquals("alice", config.getUsername());
        Assert.assertEquals("secret", config.getPassword());
        Assert.assertFalse(config.isAuthDisabled());
    }

    @Test
    public void from_shouldDisableAuthenticationWhenRequested() {
        Map<String, String> environment = new HashMap<>();
        environment.put("NEO4J_AUTH", " none ");

        Neo4jConfig config = Neo4jConfig.from(new Properties(), environment);

        Assert.assertTrue(config.isAuthDisabled());
        Assert.assertEquals(Neo4jConfig.DEFAULT_USER, config.getUsername());
        Assert.assertEquals(Neo4jConfig.DEFAULT_PASSWORD, config.getPassword());
    }
}
