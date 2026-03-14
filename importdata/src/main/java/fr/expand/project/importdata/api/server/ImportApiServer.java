package fr.expand.project.importdata.api.server;

import static spark.Spark.awaitInitialization;
import static spark.Spark.before;
import static spark.Spark.options;
import static spark.Spark.port;

public class ImportApiServer {

    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        int portValue = DEFAULT_PORT;
        if (args != null && args.length > 0) {
            try {
                portValue = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                portValue = DEFAULT_PORT;
            }
        }
        start(portValue);
    }

    public static void start(int portValue) {
        port(portValue);
        configureCors();
        registerRoutes();
        awaitInitialization();
    }

    private static void configureCors() {
        options("/*", (request, response) -> {
            addCorsHeaders(response);
            return "OK";
        });

        before((request, response) -> addCorsHeaders(response));
    }

    private static void registerRoutes() {
        ApiSupport apiSupport = new ApiSupport();
        AccessSupport accessSupport = new AccessSupport(apiSupport);
        ModelPayloadMapper modelPayloadMapper = new ModelPayloadMapper();

        new HealthRoutes(apiSupport).register();
        new AuthRoutes(apiSupport, accessSupport).register();
        new AccessRoutes(apiSupport, accessSupport).register();
        new ModelRoutes(apiSupport, accessSupport, modelPayloadMapper).register();
        new DataRoutes(apiSupport, accessSupport, modelPayloadMapper).register();
    }

    private static void addCorsHeaders(spark.Response response) {
        response.raw().setHeader("Access-Control-Allow-Origin", "*");
        response.raw().setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        response.raw().setHeader(
            "Access-Control-Allow-Headers",
            "Content-Type,Authorization,Accept,Origin,X-Session-Token"
        );
    }
}
