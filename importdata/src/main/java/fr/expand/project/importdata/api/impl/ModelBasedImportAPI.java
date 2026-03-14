package fr.expand.project.importdata.api.impl;

import java.io.File;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import fr.expand.project.importdata.dao.IConnectorDb;
import fr.expand.project.importdata.dao.connectors.impl.CypherConnector;
import fr.expand.project.importdata.dto.DataPackObject;
import fr.expand.project.importdata.dto.generated.DATAS;
import fr.expand.project.importdata.dto.generated.LINK;
import fr.expand.project.importdata.dto.generated.OBJECT;
import fr.expand.project.importdata.model.Neo4jModelStore;
import fr.expand.project.importdata.model.ModelManager;
import fr.expand.project.importdata.validation.DataValidator;
import fr.expand.project.importdata.validation.ValidationResult;

/**
 * Import API with model validation support
 */
public class ModelBasedImportAPI {
    
    private static final Logger LOGGER = LogManager.getLogger(ModelBasedImportAPI.class);
    
    private ModelManager modelManager;
    private DataValidator validator;
    private IConnectorDb connector;
    
    public ModelBasedImportAPI() {
        this.modelManager = ModelManager.getInstance();
        this.validator = new DataValidator();
        this.connector = null;
    }
    
    /**
     * Load and validate data from XML file
     * @param dataFile XML file containing data
     * @param validateOnly If true, only validate without importing
     * @return Validation result
     * @throws JAXBException If XML parsing fails
     */
    public ValidationResult importData(File dataFile, boolean validateOnly) throws JAXBException {
        LOGGER.info("Loading data from file: " + dataFile.getAbsolutePath());
        
        // Parse data XML
        DATAS data = loadDataFromFile(dataFile);

        ValidationResult result = importData(data, validateOnly);
        return result;
    }

    /**
     * Validate and import a data pack already loaded in memory.
     */
    public ValidationResult importData(DATAS data, boolean validateOnly) {
        return importData(data, validateOnly, null);
    }

    /**
     * Validate and import a data pack with an optional model key.
     */
    public ValidationResult importData(DATAS data, boolean validateOnly, String modelKey) {
        // Validate against current model
        LOGGER.info("Validating data against model...");
        ValidationResult result = validator.validate(data);

        // Log validation results
        LOGGER.info(result.getReport());

        if (!result.isValid()) {
            LOGGER.error("Validation failed. Import aborted.");
            return result;
        }

        if (validateOnly) {
            LOGGER.info("Validation-only mode. Skipping import.");
            return result;
        }

        // Import data if validation passed
        LOGGER.info("Validation successful. Starting import...");
        if (modelKey == null || modelKey.isBlank()) {
            modelKey = storeModelToNeo4j();
        }
        if (connector == null) {
            connector = new CypherConnector();
        }
        connector.setModelKey(modelKey);
        importToNeo4j(data);

        return result;
    }
    
    /**
     * Load data from XML file
     */
    private DATAS loadDataFromFile(File dataFile) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(DATAS.class);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        DATAS data = (DATAS) unmarshaller.unmarshal(dataFile);
        
        LOGGER.info("Data loaded successfully");
        if (data.getOBJECTS() != null && data.getOBJECTS().getOBJECT() != null) {
            LOGGER.info("  - Objects: " + data.getOBJECTS().getOBJECT().size());
        }
        if (data.getLINKS() != null && data.getLINKS().getLINK() != null) {
            LOGGER.info("  - Links: " + data.getLINKS().getLINK().size());
        }
        
        return data;
    }
    
    /**
     * Import validated data to Neo4j
     */
    private void importToNeo4j(DATAS data) {
        // Note: connector connects automatically in constructor
        
        try {
            // Clear existing data (optional - for demo purposes)
            // connector.deleteAll();
            
            int objectCount = 0;
            int linkCount = 0;
            
            // Import objects
            if (data.getOBJECTS() != null && data.getOBJECTS().getOBJECT() != null) {
                LOGGER.info("Importing objects...");
                for (OBJECT obj : data.getOBJECTS().getOBJECT()) {
                    // Create wrapper for compatibility
                    DataPackObject dpObj = new DataPackObject();
                    dpObj.setID(obj.getID());
                    dpObj.setTYPE(obj.getTYPE());
                    dpObj.getATTRIBUTE().addAll(obj.getATTRIBUTE());
                    
                    connector.writeObject(dpObj);
                    objectCount++;
                }
                LOGGER.info("Imported " + objectCount + " objects");
            }
            
            // Import links
            if (data.getLINKS() != null && data.getLINKS().getLINK() != null) {
                LOGGER.info("Importing links...");
                for (LINK link : data.getLINKS().getLINK()) {
                    // Create wrapper objects
                    DataPackObject objA = new DataPackObject();
                    objA.setID(link.getOBJLINKA().getID());
                    objA.setTYPE(link.getOBJLINKA().getTYPE());
                    
                    DataPackObject objB = new DataPackObject();
                    objB.setID(link.getOBJLINKB().getID());
                    objB.setTYPE(link.getOBJLINKB().getTYPE());
                    
                    // Determine if link is directed (default: true)
                    boolean isDirected = true;
                    if (modelManager.getCurrentModel() != null) {
                        var linkType = modelManager.getLinkType(link.getTYPE());
                        if (linkType != null) {
                            Boolean directed = linkType.isDIRECTED();
                            if (directed != null) {
                                isDirected = directed.booleanValue();
                            }
                        }
                    }
                    
                    connector.writeLink(objA, objB, isDirected, link.getTYPE(), link.getATTRIBUTE());
                    linkCount++;
                }
                LOGGER.info("Imported " + linkCount + " links");
            }
            
            LOGGER.info("Import completed successfully!");
            
        } catch (Exception e) {
            LOGGER.error("Error during import", e);
            throw new RuntimeException("Import failed", e);
        }
    }

    /**
     * Store the current model in Neo4j as a separate subgraph.
     */
    private String storeModelToNeo4j() {
        var model = modelManager.getCurrentModel();
        if (model == null) {
            LOGGER.warn("No model loaded, skipping model persistence");
            return null;
        }

        try (Neo4jModelStore store = new Neo4jModelStore()) {
            String modelKey = store.storeModel(model, modelManager.getCurrentModelXml());
            LOGGER.info("Model stored in Neo4j: " + model.getNAME());
            return modelKey;
        } catch (Exception e) {
            LOGGER.error("Failed to store model in Neo4j", e);
            throw new RuntimeException("Model persistence failed", e);
        }
    }
    
    /**
     * Set custom database connector
     */
    public void setConnector(IConnectorDb connector) {
        this.connector = connector;
    }
}
