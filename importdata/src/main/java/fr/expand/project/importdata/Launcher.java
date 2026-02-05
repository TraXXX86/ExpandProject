package fr.expand.project.importdata;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import fr.expand.project.importdata.api.impl.ModelBasedImportAPI;
import fr.expand.project.importdata.api.server.ImportApiServer;
import fr.expand.project.importdata.model.ModelManager;
import fr.expand.project.importdata.validation.ValidationResult;
import fr.expand.project.importdata.model.Neo4jModelStore;

/**
 * Main application launcher
 * 
 * Usage:
 *   java -jar importpackage.jar <model.xml> <data.xml> [--validate-only]
 *   
 * Examples:
 *   # Full import with validation
 *   java -jar importpackage.jar model/my_model.xml data/my_data.xml
 *   
 *   # Validation only (no import)
 *   java -jar importpackage.jar model/my_model.xml data/my_data.xml --validate-only
 *   
 *   # Using example files from resources
 *   java -cp importpackage.jar fr.expand.project.importdata.Launcher --example
 */
public class Launcher {
    
    private static final Logger LOGGER = LogManager.getLogger(Launcher.class);
    
    public static void main(String[] args) {
        LOGGER.info("========================================");
        LOGGER.info("  ExpandProject - Data Import Tool");
        LOGGER.info("========================================");
        LOGGER.info("");
        
        try {
            if (args.length == 0 || args[0].equals("--help")) {
                printUsage();
                return;
            }
            
            // Run example mode
            if (args.length == 1 && args[0].equals("--example")) {
                runExample();
                return;
            }

            // Run API server
            if (args.length >= 1 && args[0].equals("--api")) {
                int port = 8080;
                if (args.length >= 2) {
                    try {
                        port = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {
                        port = 8080;
                    }
                }
                ImportApiServer.start(port);
                return;
            }

            // Delete model and data
            if (args.length >= 2 && args[0].equals("--delete-model")) {
                String modelName = args[1];
                String modelVersion = args.length >= 3 ? args[2] : "";
                deleteModelAndData(modelName, modelVersion);
                return;
            }
            
            // Check arguments
            if (args.length < 2) {
                LOGGER.error("Error: Missing arguments");
                printUsage();
                System.exit(1);
            }
            
            String modelPath = args[0];
            String dataPath = args[1];
            boolean validateOnly = args.length > 2 && args[2].equals("--validate-only");
            
            // Run import
            runImport(modelPath, dataPath, validateOnly);
            
        } catch (Exception e) {
            LOGGER.error("Fatal error", e);
            System.exit(1);
        }
    }
    
    /**
     * Run import with model validation
     */
    private static void runImport(String modelPath, String dataPath, boolean validateOnly) {
        try {
            LOGGER.info("Configuration:");
            LOGGER.info("  Model file: " + modelPath);
            LOGGER.info("  Data file: " + dataPath);
            LOGGER.info("  Mode: " + (validateOnly ? "VALIDATE ONLY" : "VALIDATE & IMPORT"));
            LOGGER.info("");
            
            // Load model
            ModelManager modelManager = ModelManager.getInstance();
            File modelFile = new File(modelPath);
            
            if (!modelFile.exists()) {
                LOGGER.error("Model file not found: " + modelPath);
                System.exit(1);
            }
            
            modelManager.loadModel(modelFile);
            LOGGER.info("");
            
            // Load and validate data
            ModelBasedImportAPI importAPI = new ModelBasedImportAPI();
            File dataFile = new File(dataPath);
            
            if (!dataFile.exists()) {
                LOGGER.error("Data file not found: " + dataPath);
                System.exit(1);
            }
            
            ValidationResult result = importAPI.importData(dataFile, validateOnly);
            
            LOGGER.info("");
            LOGGER.info("========================================");
            if (result.isValid()) {
                LOGGER.info("  SUCCESS");
                if (!validateOnly) {
                    LOGGER.info("  Data imported to Neo4j");
                }
            } else {
                LOGGER.error("  FAILED - Validation errors found");
                System.exit(1);
            }
            LOGGER.info("========================================");
            
        } catch (Exception e) {
            LOGGER.error("Error during import", e);
            System.exit(1);
        }
    }
    
    /**
     * Run example with bundled files
     */
    private static void runExample() {
        try {
            LOGGER.info("Running EXAMPLE mode with bundled files");
            LOGGER.info("");
            
            // Load example model from resources
            ModelManager modelManager = ModelManager.getInstance();
            modelManager.loadModelFromResource("model/example_social_network_model.xml");
            LOGGER.info("");
            
            // Validate example data
            ModelBasedImportAPI importAPI = new ModelBasedImportAPI();
            
            LOGGER.info("Loading example data from resources...");
            jakarta.xml.bind.JAXBContext jaxbContext = 
                jakarta.xml.bind.JAXBContext.newInstance(
                    fr.expand.project.importdata.dto.generated.DATAS.class);
            jakarta.xml.bind.Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            fr.expand.project.importdata.dto.generated.DATAS data = 
                (fr.expand.project.importdata.dto.generated.DATAS) unmarshaller.unmarshal(
                    Launcher.class.getClassLoader().getResourceAsStream(
                        "datapack/example_social_network_data.xml"));
            
            LOGGER.info("");
            LOGGER.info("Validating example data...");
            fr.expand.project.importdata.validation.DataValidator validator = 
                new fr.expand.project.importdata.validation.DataValidator();
            ValidationResult result = validator.validate(data);
            
            LOGGER.info("");
            LOGGER.info("========================================");
            if (result.isValid()) {
                LOGGER.info("  EXAMPLE VALIDATION SUCCESS");
                LOGGER.info("  The bundled example data is valid!");
            } else {
                LOGGER.error("  EXAMPLE VALIDATION FAILED");
            }
            LOGGER.info("========================================");
            
        } catch (Exception e) {
            LOGGER.error("Error running example", e);
            System.exit(1);
        }
    }
    
    /**
     * Print usage information
     */
    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -jar importpackage.jar <model.xml> <data.xml> [--validate-only]");
        System.out.println("  java -jar importpackage.jar --delete-model <modelName> [modelVersion]");
        System.out.println("  java -jar importpackage.jar --example");
        System.out.println("  java -jar importpackage.jar --api [port]");
        System.out.println("  java -jar importpackage.jar --help");
        System.out.println("");
        System.out.println("Arguments:");
        System.out.println("  model.xml        Path to the data model XML file");
        System.out.println("  data.xml         Path to the data XML file to import");
        System.out.println("  --validate-only  Only validate, do not import to database");
        System.out.println("  --delete-model   Delete a model and its associated data from the database");
        System.out.println("  --example        Run validation on bundled example files");
        System.out.println("  --api            Start the HTTP API server (default port 8080)");
        System.out.println("  --help           Show this help message");
        System.out.println("");
        System.out.println("Examples:");
        System.out.println("  # Import with validation");
        System.out.println("  java -jar importpackage.jar model/my_model.xml data/my_data.xml");
        System.out.println("");
        System.out.println("  # Validate only (no import)");
        System.out.println("  java -jar importpackage.jar model/my_model.xml data/my_data.xml --validate-only");
        System.out.println("");
        System.out.println("  # Delete a model and its data");
        System.out.println("  java -jar importpackage.jar --delete-model MonModele 1.0");
        System.out.println("");
        System.out.println("  # Test with example data");
        System.out.println("  java -jar importpackage.jar --example");
        System.out.println("");
        System.out.println("  # Start API server");
        System.out.println("  java -jar importpackage.jar --api 8080");
    }

    private static void deleteModelAndData(String modelName, String modelVersion) {
        LOGGER.info("Deleting model and data:");
        LOGGER.info("  Model name: " + modelName);
        LOGGER.info("  Model version: " + modelVersion);

        try (Neo4jModelStore store = new Neo4jModelStore()) {
            store.deleteModelAndData(modelName, modelVersion);
            LOGGER.info("Model and associated data deleted successfully.");
        } catch (Exception e) {
            LOGGER.error("Error deleting model and data", e);
            System.exit(1);
        }
    }
}
