package fr.expand.project.importdata.model;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import fr.expand.project.importdata.model.generated.DATAMODEL;
import fr.expand.project.importdata.model.generated.LINKTYPE;
import fr.expand.project.importdata.model.generated.OBJECTTYPE;

/**
 * Manager for data models
 * Handles loading, caching and accessing model definitions
 */
public class ModelManager {
    
    private static final Logger LOGGER = LogManager.getLogger(ModelManager.class);
    
    private static ModelManager instance;
    private Map<String, DATAMODEL> loadedModels;
    private DATAMODEL currentModel;
    
    private ModelManager() {
        loadedModels = new HashMap<>();
    }
    
    /**
     * Get singleton instance
     */
    public static ModelManager getInstance() {
        if (instance == null) {
            instance = new ModelManager();
        }
        return instance;
    }
    
    /**
     * Load a model from XML file
     * @param modelFile The XML file containing the model
     * @return The loaded data model
     * @throws JAXBException If parsing fails
     */
    public DATAMODEL loadModel(File modelFile) throws JAXBException {
        LOGGER.info("Loading model from file: " + modelFile.getAbsolutePath());
        
        JAXBContext jaxbContext = JAXBContext.newInstance(DATAMODEL.class);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        DATAMODEL model = (DATAMODEL) unmarshaller.unmarshal(modelFile);
        
        // Cache the model
        loadedModels.put(model.getNAME(), model);
        currentModel = model;
        
        LOGGER.info("Model loaded successfully: " + model.getNAME() + " (version " + model.getVERSION() + ")");
        LOGGER.info("  - Object types: " + model.getOBJECTTYPES().getOBJECTTYPE().size());
        LOGGER.info("  - Link types: " + model.getLINKTYPES().getLINKTYPE().size());
        
        return model;
    }
    
    /**
     * Load a model from classpath resource
     * @param resourcePath Path to the resource
     * @return The loaded data model
     * @throws JAXBException If parsing fails
     */
    public DATAMODEL loadModelFromResource(String resourcePath) throws JAXBException {
        LOGGER.info("Loading model from resource: " + resourcePath);
        
        JAXBContext jaxbContext = JAXBContext.newInstance(DATAMODEL.class);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        DATAMODEL model = (DATAMODEL) unmarshaller.unmarshal(
            getClass().getClassLoader().getResourceAsStream(resourcePath)
        );
        
        // Cache the model
        loadedModels.put(model.getNAME(), model);
        currentModel = model;
        
        LOGGER.info("Model loaded successfully: " + model.getNAME());
        
        return model;
    }
    
    /**
     * Get the current active model
     * @return Current model or null if none loaded
     */
    public DATAMODEL getCurrentModel() {
        return currentModel;
    }
    
    /**
     * Set the current active model
     * @param modelName Name of the model to activate
     * @return true if model found and activated, false otherwise
     */
    public boolean setCurrentModel(String modelName) {
        DATAMODEL model = loadedModels.get(modelName);
        if (model != null) {
            currentModel = model;
            LOGGER.info("Current model set to: " + modelName);
            return true;
        }
        LOGGER.warn("Model not found: " + modelName);
        return false;
    }
    
    /**
     * Get a specific object type definition
     * @param typeName Name of the object type
     * @return Object type definition or null if not found
     */
    public OBJECTTYPE getObjectType(String typeName) {
        if (currentModel == null) {
            LOGGER.warn("No model loaded");
            return null;
        }
        
        for (OBJECTTYPE objType : currentModel.getOBJECTTYPES().getOBJECTTYPE()) {
            if (objType.getNAME().equals(typeName)) {
                return objType;
            }
        }
        
        LOGGER.warn("Object type not found: " + typeName);
        return null;
    }
    
    /**
     * Get a specific link type definition
     * @param typeName Name of the link type
     * @return Link type definition or null if not found
     */
    public LINKTYPE getLinkType(String typeName) {
        if (currentModel == null) {
            LOGGER.warn("No model loaded");
            return null;
        }
        
        for (LINKTYPE linkType : currentModel.getLINKTYPES().getLINKTYPE()) {
            if (linkType.getNAME().equals(typeName)) {
                return linkType;
            }
        }
        
        LOGGER.warn("Link type not found: " + typeName);
        return null;
    }
    
    /**
     * Check if an object type exists in the current model
     * @param typeName Name of the object type
     * @return true if exists, false otherwise
     */
    public boolean objectTypeExists(String typeName) {
        return getObjectType(typeName) != null;
    }
    
    /**
     * Check if a link type exists in the current model
     * @param typeName Name of the link type
     * @return true if exists, false otherwise
     */
    public boolean linkTypeExists(String typeName) {
        return getLinkType(typeName) != null;
    }
    
    /**
     * Clear all loaded models
     */
    public void clearModels() {
        loadedModels.clear();
        currentModel = null;
        LOGGER.info("All models cleared");
    }
    
    /**
     * Get all loaded models
     * @return Map of model names to model instances
     */
    public Map<String, DATAMODEL> getLoadedModels() {
        return new HashMap<>(loadedModels);
    }
}
