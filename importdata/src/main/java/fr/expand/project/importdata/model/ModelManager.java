package fr.expand.project.importdata.model;

import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import fr.expand.project.importdata.model.generated.DATAMODEL;
import fr.expand.project.importdata.model.generated.ATTRIBUTEDEFINITION;
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
    private String currentModelXml;
    private Map<String, OBJECTTYPE> objectTypeIndex;
    private Map<String, LINKTYPE> linkTypeIndex;
    
    private ModelManager() {
        loadedModels = new HashMap<>();
        objectTypeIndex = new HashMap<>();
        linkTypeIndex = new HashMap<>();
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

        try {
            currentModelXml = Files.readString(modelFile.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.warn("Unable to read model XML content", e);
            currentModelXml = null;
        }

        JAXBContext jaxbContext = JAXBContext.newInstance(DATAMODEL.class);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        DATAMODEL model = (DATAMODEL) unmarshaller.unmarshal(modelFile);
        
        // Cache the model
        loadedModels.put(model.getNAME(), model);
        currentModel = model;
        indexCurrentModel();
        validateInheritance(currentModel);
        
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

        InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new JAXBException("Resource not found: " + resourcePath);
        }
        try {
            currentModelXml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.warn("Unable to read model XML content from resource", e);
            currentModelXml = null;
        }

        JAXBContext jaxbContext = JAXBContext.newInstance(DATAMODEL.class);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        DATAMODEL model = (DATAMODEL) unmarshaller.unmarshal(
            getClass().getClassLoader().getResourceAsStream(resourcePath)
        );
        
        // Cache the model
        loadedModels.put(model.getNAME(), model);
        currentModel = model;
        indexCurrentModel();
        validateInheritance(currentModel);
        
        LOGGER.info("Model loaded successfully: " + model.getNAME());
        
        return model;
    }

    /**
     * Load a model from a raw XML string.
     */
    public DATAMODEL loadModelFromXml(String xmlContent) throws JAXBException {
        if (xmlContent == null || xmlContent.isBlank()) {
            throw new JAXBException("Model XML content is empty");
        }

        LOGGER.info("Loading model from raw XML content");
        JAXBContext jaxbContext = JAXBContext.newInstance(DATAMODEL.class);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        DATAMODEL model = (DATAMODEL) unmarshaller.unmarshal(new StringReader(xmlContent));

        loadedModels.put(model.getNAME(), model);
        currentModel = model;
        currentModelXml = xmlContent;
        indexCurrentModel();
        validateInheritance(currentModel);

        LOGGER.info("Model loaded successfully: " + model.getNAME() + " (version " + model.getVERSION() + ")");
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
     * Return the raw XML for the current model, if available.
     */
    public String getCurrentModelXml() {
        return currentModelXml;
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
            currentModelXml = null;
            indexCurrentModel();
            validateInheritance(currentModel);
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

        OBJECTTYPE objectType = objectTypeIndex.get(typeName);
        if (objectType == null) {
            LOGGER.warn("Object type not found: " + typeName);
        }
        return objectType;
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

        LINKTYPE linkType = linkTypeIndex.get(typeName);
        if (linkType == null) {
            LOGGER.warn("Link type not found: " + typeName);
        }
        return linkType;
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
        currentModelXml = null;
        objectTypeIndex.clear();
        linkTypeIndex.clear();
        LOGGER.info("All models cleared");
    }
    
    /**
     * Get all loaded models
     * @return Map of model names to model instances
     */
    public Map<String, DATAMODEL> getLoadedModels() {
        return new HashMap<>(loadedModels);
    }

    /**
     * Build indexes for object and link types for the current model.
     */
    private void indexCurrentModel() {
        objectTypeIndex.clear();
        linkTypeIndex.clear();

        if (currentModel == null) {
            return;
        }

        if (currentModel.getOBJECTTYPES() != null && currentModel.getOBJECTTYPES().getOBJECTTYPE() != null) {
            for (OBJECTTYPE objType : currentModel.getOBJECTTYPES().getOBJECTTYPE()) {
                objectTypeIndex.put(objType.getNAME(), objType);
            }
        }

        if (currentModel.getLINKTYPES() != null && currentModel.getLINKTYPES().getLINKTYPE() != null) {
            for (LINKTYPE linkType : currentModel.getLINKTYPES().getLINKTYPE()) {
                linkTypeIndex.put(linkType.getNAME(), linkType);
            }
        }
    }

    /**
     * Resolve attribute definitions for a type, including inherited ones.
     */
    public Map<String, ATTRIBUTEDEFINITION> getAttributeDefinitionMap(OBJECTTYPE objectType) {
        Map<String, ATTRIBUTEDEFINITION> merged = new LinkedHashMap<>();
        if (objectType == null) {
            return merged;
        }

        for (OBJECTTYPE type : getHierarchyRootFirst(objectType)) {
            if (type.getATTRIBUTEDEFINITIONS() == null || type.getATTRIBUTEDEFINITIONS().getATTRIBUTEDEFINITION() == null) {
                continue;
            }
            for (ATTRIBUTEDEFINITION def : type.getATTRIBUTEDEFINITIONS().getATTRIBUTEDEFINITION()) {
                if (def.getNAME() == null) {
                    continue;
                }
                merged.put(def.getNAME(), def);
            }
        }

        return merged;
    }

    /**
     * Check if a candidate type is the same as, or inherits from, an allowed type.
     */
    public boolean isTypeOrSubtype(String candidateType, String allowedType) {
        if (candidateType == null || allowedType == null) {
            return false;
        }

        if (candidateType.equals(allowedType)) {
            return true;
        }

        OBJECTTYPE current = objectTypeIndex.get(candidateType);
        Set<String> visited = new HashSet<>();
        while (current != null) {
            String parentName = current.getPARENT();
            if (parentName == null || parentName.trim().isEmpty()) {
                return false;
            }
            if (!visited.add(parentName)) {
                return false;
            }
            if (parentName.equals(allowedType)) {
                return true;
            }
            current = objectTypeIndex.get(parentName);
        }

        return false;
    }

    private List<OBJECTTYPE> getHierarchyRootFirst(OBJECTTYPE objectType) {
        List<OBJECTTYPE> chain = new ArrayList<>();
        if (objectType == null) {
            return chain;
        }

        Set<String> visited = new HashSet<>();
        OBJECTTYPE current = objectType;
        while (current != null) {
            String name = current.getNAME();
            if (name != null && !visited.add(name)) {
                break;
            }
            chain.add(current);
            String parentName = current.getPARENT();
            if (parentName == null || parentName.trim().isEmpty()) {
                break;
            }
            current = objectTypeIndex.get(parentName);
        }

        // Reverse to have root-first order
        List<OBJECTTYPE> rootFirst = new ArrayList<>();
        for (int i = chain.size() - 1; i >= 0; i--) {
            rootFirst.add(chain.get(i));
        }

        return rootFirst;
    }

    private void validateInheritance(DATAMODEL model) {
        if (model == null || model.getOBJECTTYPES() == null || model.getOBJECTTYPES().getOBJECTTYPE() == null) {
            return;
        }

        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();

        for (OBJECTTYPE objType : model.getOBJECTTYPES().getOBJECTTYPE()) {
            String parentName = objType.getPARENT();
            if (parentName != null && !parentName.trim().isEmpty() && !objectTypeIndex.containsKey(parentName)) {
                LOGGER.error("Invalid inheritance: object type '" + objType.getNAME()
                    + "' references unknown parent '" + parentName + "'");
            }

            String typeName = objType.getNAME();
            if (typeName != null && hasInheritanceCycle(typeName, visiting, visited)) {
                LOGGER.error("Inheritance cycle detected involving type: " + typeName);
            }
        }
    }

    private boolean hasInheritanceCycle(String typeName, Set<String> visiting, Set<String> visited) {
        if (visited.contains(typeName)) {
            return false;
        }
        if (visiting.contains(typeName)) {
            return true;
        }

        visiting.add(typeName);
        OBJECTTYPE type = objectTypeIndex.get(typeName);
        if (type != null) {
            String parentName = type.getPARENT();
            if (parentName != null && !parentName.trim().isEmpty() && objectTypeIndex.containsKey(parentName)) {
                if (hasInheritanceCycle(parentName, visiting, visited)) {
                    return true;
                }
            }
        }
        visiting.remove(typeName);
        visited.add(typeName);
        return false;
    }
}
