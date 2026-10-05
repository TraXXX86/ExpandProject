package fr.expand.project.importdata.dao;

import org.neo4j.driver.*;

/** One thread-safe driver per application; each operation owns and closes its session. */
public final class Neo4jDriverProvider {
    private static Driver driver;

    private Neo4jDriverProvider() {}

    public static synchronized Driver getDriver() {
        if (driver == null) {
            String auth = setting("NEO4J_AUTH", null, null);
            AuthToken token;
            if ("none".equalsIgnoreCase(auth)) token = AuthTokens.none();
            else if (auth != null && auth.contains("/")) {
                int separator = auth.indexOf('/');
                if (separator == 0 || separator == auth.length() - 1)
                    throw new IllegalStateException(
                            "NEO4J_AUTH must include a username and password");
                token =
                        AuthTokens.basic(
                                auth.substring(0, separator), auth.substring(separator + 1));
            } else {
                if (auth != null)
                    throw new IllegalStateException(
                            "NEO4J_AUTH must be user/password or the explicit value none");
                String password = setting("NEO4J_PASSWORD", null, null);
                if (password == null)
                    throw new IllegalStateException(
                            "Set NEO4J_AUTH=user/password or NEO4J_PASSWORD before connecting");
                token = AuthTokens.basic(setting("NEO4J_USER", null, "neo4j"), password);
            }
            Driver candidate =
                    GraphDatabase.driver(
                            setting("NEO4J_BOLT_URI", "NEO4J_URI", "bolt://localhost:7687"), token);
            try (Session session = candidate.session()) {
                session.run(
                                "CREATE CONSTRAINT data_object_identity IF NOT EXISTS FOR"
                                        + " (n:DataObject) REQUIRE (n.modelKey,n.type,n.dataId) IS"
                                        + " UNIQUE")
                        .consume();
                session.run(
                                "CREATE CONSTRAINT data_model_key IF NOT EXISTS FOR (n:DataModel)"
                                        + " REQUIRE n.key IS UNIQUE")
                        .consume();
                session.run(
                                "CREATE INDEX data_object_model IF NOT EXISTS FOR (n:DataObject) ON"
                                        + " (n.modelKey)")
                        .consume();
                session.run(
                                "CREATE FULLTEXT INDEX data_object_search IF NOT EXISTS FOR"
                                        + " (n:DataObject) ON EACH [n.searchText]")
                        .consume();
                session.run(
                                "CREATE INDEX audit_model_time IF NOT EXISTS FOR (n:AuditData) ON"
                                        + " (n.modelKey,n.timestamp)")
                        .consume();
                session.run(
                                "CREATE CONSTRAINT workflow_definition_identity IF NOT EXISTS FOR"
                                    + " (w:WorkflowDefinition) REQUIRE (w.modelKey,w.id,w.version)"
                                    + " IS UNIQUE")
                        .consume();
                session.run(
                                "CREATE CONSTRAINT workflow_binding_identity IF NOT EXISTS FOR"
                                    + " (w:WorkflowBinding) REQUIRE (w.modelKey,w.objectType) IS"
                                    + " UNIQUE")
                        .consume();
                session.run(
                                "CREATE INDEX object_workflow_status IF NOT EXISTS FOR"
                                    + " (n:DataObject) ON (n.modelKey,n._workflowState)")
                        .consume();
                session.run("CALL db.awaitIndex('data_object_search',30)").consume();
            } catch (RuntimeException e) {
                candidate.close();
                throw e;
            }
            driver = candidate;
            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(Neo4jDriverProvider::close, "neo4j-driver-shutdown"));
        }
        return driver;
    }

    public static synchronized void close() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    private static String setting(String key, String fallback, String defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) value = System.getenv(key);
        if ((value == null || value.isBlank()) && fallback != null)
            return setting(fallback, null, defaultValue);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
