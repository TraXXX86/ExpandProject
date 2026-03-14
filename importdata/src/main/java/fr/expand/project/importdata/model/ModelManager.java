package fr.expand.project.importdata.model;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import fr.expand.project.importdata.model.generated.ATTRIBUTEDEFINITION;
import fr.expand.project.importdata.model.generated.ATTRIBUTEGROUP;
import fr.expand.project.importdata.model.generated.ATTRIBUTEREF;
import fr.expand.project.importdata.model.generated.DATAMODEL;
import fr.expand.project.importdata.model.generated.LINKTYPE;
import fr.expand.project.importdata.model.generated.OBJECTTYPE;

/**
 * Manager for data models.
 * Handles loading, caching and accessing model definitions.
 */
public class ModelManager {

    private static final Logger LOGGER = LogManager.getLogger(ModelManager.class);
    private static final ModelManager INSTANCE = new ModelManager();

    private final Map<String, DATAMODEL> loadedModels;
    private volatile ModelContext currentContext;

    private ModelManager() {
        loadedModels = new ConcurrentHashMap<>();
    }

    /**
     * Get singleton instance.
     */
    public static ModelManager getInstance() {
        return INSTANCE;
    }

    /**
     * Load a model without mutating the globally active model.
     */
    public ModelContext loadModelContext(File modelFile) throws JAXBException {
        LOGGER.info("Loading model from file: " + modelFile.getAbsolutePath());
        try {
            return buildModelContextFromXml(Files.readString(modelFile.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new JAXBException("Unable to read model file: " + modelFile.getAbsolutePath(), e);
        }
    }

    /**
     * Load a model resource without mutating the globally active model.
     */
    public ModelContext loadModelContextFromResource(String resourcePath) throws JAXBException {
        LOGGER.info("Loading model from resource: " + resourcePath);
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new JAXBException("Resource not found: " + resourcePath);
            }
            return buildModelContextFromXml(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new JAXBException("Unable to read model resource: " + resourcePath, e);
        }
    }

    /**
     * Load a model from raw XML without mutating the globally active model.
     */
    public ModelContext loadModelContextFromXml(String xmlContent) throws JAXBException {
        LOGGER.info("Loading model from raw XML content");
        return buildModelContextFromXml(xmlContent);
    }

    /**
     * Load a model from XML file and make it the current model for legacy callers.
     * @param modelFile The XML file containing the model
     * @return The loaded data model
     * @throws JAXBException If parsing fails
     */
    public DATAMODEL loadModel(File modelFile) throws JAXBException {
        ModelContext context = loadModelContext(modelFile);
        activateContext(context);
        return context.getModel();
    }

    /**
     * Load a model from classpath resource and make it the current model for legacy callers.
     * @param resourcePath Path to the resource
     * @return The loaded data model
     * @throws JAXBException If parsing fails
     */
    public DATAMODEL loadModelFromResource(String resourcePath) throws JAXBException {
        ModelContext context = loadModelContextFromResource(resourcePath);
        activateContext(context);
        return context.getModel();
    }

    /**
     * Load a model from a raw XML string and make it the current model for legacy callers.
     */
    public DATAMODEL loadModelFromXml(String xmlContent) throws JAXBException {
        ModelContext context = loadModelContextFromXml(xmlContent);
        activateContext(context);
        return context.getModel();
    }

    /**
     * Get the current active model.
     * @return Current model or null if none loaded
     */
    public DATAMODEL getCurrentModel() {
        ModelContext context = currentContext;
        return context == null ? null : context.getModel();
    }

    /**
     * Return the raw XML for the current model, if available.
     */
    public String getCurrentModelXml() {
        ModelContext context = currentContext;
        return context == null ? null : context.getModelXml();
    }

    /**
     * Return the current immutable model context, if available.
     */
    public ModelContext getCurrentContext() {
        return currentContext;
    }

    /**
     * Set the current active model.
     * @param modelName Name of the model to activate
     * @return true if model found and activated, false otherwise
     */
    public boolean setCurrentModel(String modelName) {
        DATAMODEL model = loadedModels.get(modelName);
        if (model != null) {
            currentContext = buildModelContext(model, null);
            LOGGER.info("Current model set to: " + modelName);
            return true;
        }
        LOGGER.warn("Model not found: " + modelName);
        return false;
    }

    /**
     * Get a specific object type definition from the current model.
     * @param typeName Name of the object type
     * @return Object type definition or null if not found
     */
    public OBJECTTYPE getObjectType(String typeName) {
        ModelContext context = currentContext;
        if (context == null) {
            LOGGER.warn("No model loaded");
            return null;
        }

        OBJECTTYPE objectType = context.getObjectType(typeName);
        if (objectType == null) {
            LOGGER.warn("Object type not found: " + typeName);
        }
        return objectType;
    }

    /**
     * Get a specific link type definition from the current model.
     * @param typeName Name of the link type
     * @return Link type definition or null if not found
     */
    public LINKTYPE getLinkType(String typeName) {
        ModelContext context = currentContext;
        if (context == null) {
            LOGGER.warn("No model loaded");
            return null;
        }

        LINKTYPE linkType = context.getLinkType(typeName);
        if (linkType == null) {
            LOGGER.warn("Link type not found: " + typeName);
        }
        return linkType;
    }

    /**
     * Check if an object type exists in the current model.
     * @param typeName Name of the object type
     * @return true if exists, false otherwise
     */
    public boolean objectTypeExists(String typeName) {
        return getObjectType(typeName) != null;
    }

    /**
     * Check if a link type exists in the current model.
     * @param typeName Name of the link type
     * @return true if exists, false otherwise
     */
    public boolean linkTypeExists(String typeName) {
        return getLinkType(typeName) != null;
    }

    /**
     * Clear all loaded models.
     */
    public void clearModels() {
        loadedModels.clear();
        currentContext = null;
        LOGGER.info("All models cleared");
    }

    /**
     * Get all loaded models.
     * @return Map of model names to model instances
     */
    public Map<String, DATAMODEL> getLoadedModels() {
        return new HashMap<>(loadedModels);
    }

    /**
     * Resolve attribute definitions for a type, including inherited ones.
     */
    public Map<String, ATTRIBUTEDEFINITION> getAttributeDefinitionMap(OBJECTTYPE objectType) {
        ModelContext context = currentContext;
        if (context == null) {
            return new LinkedHashMap<>();
        }
        return context.getAttributeDefinitionMap(objectType);
    }

    /**
     * Check if a candidate type is the same as, or inherits from, an allowed type.
     */
    public boolean isTypeOrSubtype(String candidateType, String allowedType) {
        ModelContext context = currentContext;
        return context != null && context.isTypeOrSubtype(candidateType, allowedType);
    }

    private ModelContext buildModelContextFromXml(String xmlContent) throws JAXBException {
        if (xmlContent == null || xmlContent.isBlank()) {
            throw new JAXBException("Model XML content is empty");
        }

        JAXBContext jaxbContext = JAXBContext.newInstance(DATAMODEL.class);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        DATAMODEL model = (DATAMODEL) unmarshaller.unmarshal(new StringReader(xmlContent));
        ModelContext context = buildModelContext(model, xmlContent);

        LOGGER.info("Model loaded successfully: " + model.getNAME() + " (version " + model.getVERSION() + ")");
        LOGGER.info("  - Object types: " + countObjectTypes(model));
        LOGGER.info("  - Link types: " + countLinkTypes(model));
        return context;
    }

    private void activateContext(ModelContext context) {
        DATAMODEL model = context.getModel();
        if (model != null && model.getNAME() != null) {
            loadedModels.put(model.getNAME(), model);
        }
        currentContext = context;
    }

    private static ModelContext buildModelContext(DATAMODEL model, String xmlContent) {
        Map<String, OBJECTTYPE> objectTypeIndex = new LinkedHashMap<>();
        Map<String, LINKTYPE> linkTypeIndex = new LinkedHashMap<>();

        if (model != null && model.getOBJECTTYPES() != null && model.getOBJECTTYPES().getOBJECTTYPE() != null) {
            for (OBJECTTYPE objType : model.getOBJECTTYPES().getOBJECTTYPE()) {
                if (objType != null && objType.getNAME() != null) {
                    objectTypeIndex.put(objType.getNAME(), objType);
                }
            }
        }

        if (model != null && model.getLINKTYPES() != null && model.getLINKTYPES().getLINKTYPE() != null) {
            for (LINKTYPE linkType : model.getLINKTYPES().getLINKTYPE()) {
                if (linkType != null && linkType.getNAME() != null) {
                    linkTypeIndex.put(linkType.getNAME(), linkType);
                }
            }
        }

        ModelContext context = new ModelContext(model, xmlContent, objectTypeIndex, linkTypeIndex);
        validateInheritance(context);
        validateAttributeGroups(context);
        validateRepresentativeAttributes(context);
        return context;
    }

    private static int countObjectTypes(DATAMODEL model) {
        if (model == null || model.getOBJECTTYPES() == null || model.getOBJECTTYPES().getOBJECTTYPE() == null) {
            return 0;
        }
        return model.getOBJECTTYPES().getOBJECTTYPE().size();
    }

    private static int countLinkTypes(DATAMODEL model) {
        if (model == null || model.getLINKTYPES() == null || model.getLINKTYPES().getLINKTYPE() == null) {
            return 0;
        }
        return model.getLINKTYPES().getLINKTYPE().size();
    }

    private static Map<String, ATTRIBUTEDEFINITION> buildAttributeDefinitionMap(ModelContext context, OBJECTTYPE objectType) {
        Map<String, ATTRIBUTEDEFINITION> merged = new LinkedHashMap<>();
        if (context == null || objectType == null) {
            return merged;
        }

        for (OBJECTTYPE type : getHierarchyRootFirst(context, objectType)) {
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

    private static boolean isTypeOrSubtype(ModelContext context, String candidateType, String allowedType) {
        if (context == null || candidateType == null || allowedType == null) {
            return false;
        }

        if (candidateType.equals(allowedType)) {
            return true;
        }

        OBJECTTYPE current = context.objectTypeIndex.get(candidateType);
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
            current = context.objectTypeIndex.get(parentName);
        }

        return false;
    }

    private static List<OBJECTTYPE> getHierarchyRootFirst(ModelContext context, OBJECTTYPE objectType) {
        List<OBJECTTYPE> chain = new ArrayList<>();
        if (context == null || objectType == null) {
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
            current = context.objectTypeIndex.get(parentName);
        }

        List<OBJECTTYPE> rootFirst = new ArrayList<>();
        for (int i = chain.size() - 1; i >= 0; i--) {
            rootFirst.add(chain.get(i));
        }
        return rootFirst;
    }

    private static void validateInheritance(ModelContext context) {
        DATAMODEL model = context == null ? null : context.getModel();
        if (model == null || model.getOBJECTTYPES() == null || model.getOBJECTTYPES().getOBJECTTYPE() == null) {
            return;
        }

        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();

        for (OBJECTTYPE objType : model.getOBJECTTYPES().getOBJECTTYPE()) {
            String parentName = objType.getPARENT();
            if (parentName != null && !parentName.trim().isEmpty() && !context.objectTypeIndex.containsKey(parentName)) {
                LOGGER.error("Invalid inheritance: object type '" + objType.getNAME()
                    + "' references unknown parent '" + parentName + "'");
            }

            String typeName = objType.getNAME();
            if (typeName != null && hasInheritanceCycle(context, typeName, visiting, visited)) {
                LOGGER.error("Inheritance cycle detected involving type: " + typeName);
            }
        }
    }

    private static void validateAttributeGroups(ModelContext context) {
        DATAMODEL model = context == null ? null : context.getModel();
        if (model == null || model.getOBJECTTYPES() == null || model.getOBJECTTYPES().getOBJECTTYPE() == null) {
            return;
        }

        for (OBJECTTYPE objectType : model.getOBJECTTYPES().getOBJECTTYPE()) {
            Map<String, ATTRIBUTEDEFINITION> definitions = buildAttributeDefinitionMap(context, objectType);
            if (objectType.getATTRIBUTEGROUPS() == null
                || objectType.getATTRIBUTEGROUPS().getATTRIBUTEGROUP() == null) {
                continue;
            }

            for (ATTRIBUTEGROUP group : objectType.getATTRIBUTEGROUPS().getATTRIBUTEGROUP()) {
                String groupName = group.getNAME() == null ? "" : group.getNAME();
                if (group.getATTRIBUTEREF() == null || group.getATTRIBUTEREF().isEmpty()) {
                    LOGGER.warn("Empty attribute group '" + groupName + "' in type '" + objectType.getNAME() + "'");
                    continue;
                }

                for (ATTRIBUTEREF ref : group.getATTRIBUTEREF()) {
                    String attributeName = ref.getNAME();
                    if (attributeName == null || attributeName.trim().isEmpty()) {
                        LOGGER.error("Attribute group '" + groupName + "' in type '" + objectType.getNAME()
                            + "' references an empty attribute name");
                        continue;
                    }
                    if (!definitions.containsKey(attributeName)) {
                        LOGGER.error("Attribute group '" + groupName + "' in type '" + objectType.getNAME()
                            + "' references unknown attribute '" + attributeName + "'");
                    }
                }
            }
        }
    }

    private static void validateRepresentativeAttributes(ModelContext context) {
        DATAMODEL model = context == null ? null : context.getModel();
        if (model == null || model.getOBJECTTYPES() == null || model.getOBJECTTYPES().getOBJECTTYPE() == null) {
            return;
        }

        for (OBJECTTYPE objectType : model.getOBJECTTYPES().getOBJECTTYPE()) {
            Map<String, ATTRIBUTEDEFINITION> definitions = buildAttributeDefinitionMap(context, objectType);
            if (objectType.getREPRESENTATIVEATTRIBUTES() == null
                || objectType.getREPRESENTATIVEATTRIBUTES().getATTRIBUTEREF() == null) {
                continue;
            }

            List<ATTRIBUTEREF> refs = objectType.getREPRESENTATIVEATTRIBUTES().getATTRIBUTEREF();
            if (refs.isEmpty()) {
                LOGGER.warn("Empty representative attributes definition in type '" + objectType.getNAME() + "'");
                continue;
            }

            for (ATTRIBUTEREF ref : refs) {
                String attributeName = ref.getNAME();
                if (attributeName == null || attributeName.trim().isEmpty()) {
                    LOGGER.error("Representative attributes in type '" + objectType.getNAME()
                        + "' references an empty attribute name");
                    continue;
                }
                if (!definitions.containsKey(attributeName)) {
                    LOGGER.error("Representative attributes in type '" + objectType.getNAME()
                        + "' references unknown attribute '" + attributeName + "'");
                }
            }
        }
    }

    private static boolean hasInheritanceCycle(
        ModelContext context,
        String typeName,
        Set<String> visiting,
        Set<String> visited
    ) {
        if (visited.contains(typeName)) {
            return false;
        }
        if (visiting.contains(typeName)) {
            return true;
        }

        visiting.add(typeName);
        OBJECTTYPE type = context.objectTypeIndex.get(typeName);
        if (type != null) {
            String parentName = type.getPARENT();
            if (parentName != null && !parentName.trim().isEmpty() && context.objectTypeIndex.containsKey(parentName)) {
                if (hasInheritanceCycle(context, parentName, visiting, visited)) {
                    return true;
                }
            }
        }
        visiting.remove(typeName);
        visited.add(typeName);
        return false;
    }

    /**
     * Immutable model data used to keep request-scoped model handling explicit.
     */
    public static final class ModelContext {

        private final DATAMODEL model;
        private final String modelXml;
        private final Map<String, OBJECTTYPE> objectTypeIndex;
        private final Map<String, LINKTYPE> linkTypeIndex;

        private ModelContext(
            DATAMODEL model,
            String modelXml,
            Map<String, OBJECTTYPE> objectTypeIndex,
            Map<String, LINKTYPE> linkTypeIndex
        ) {
            this.model = model;
            this.modelXml = modelXml;
            this.objectTypeIndex = Collections.unmodifiableMap(new LinkedHashMap<>(objectTypeIndex));
            this.linkTypeIndex = Collections.unmodifiableMap(new LinkedHashMap<>(linkTypeIndex));
        }

        public DATAMODEL getModel() {
            return model;
        }

        public String getModelXml() {
            return modelXml;
        }

        public OBJECTTYPE getObjectType(String typeName) {
            if (typeName == null) {
                return null;
            }
            return objectTypeIndex.get(typeName);
        }

        public LINKTYPE getLinkType(String typeName) {
            if (typeName == null) {
                return null;
            }
            return linkTypeIndex.get(typeName);
        }

        public Map<String, ATTRIBUTEDEFINITION> getAttributeDefinitionMap(OBJECTTYPE objectType) {
            return buildAttributeDefinitionMap(this, objectType);
        }

        public boolean isTypeOrSubtype(String candidateType, String allowedType) {
            return ModelManager.isTypeOrSubtype(this, candidateType, allowedType);
        }
    }
}
