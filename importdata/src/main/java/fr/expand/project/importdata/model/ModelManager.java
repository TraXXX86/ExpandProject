package fr.expand.project.importdata.model;

import fr.expand.project.importdata.model.generated.ATTRIBUTEDEFINITION;
import fr.expand.project.importdata.model.generated.ATTRIBUTEGROUP;
import fr.expand.project.importdata.model.generated.ATTRIBUTEREF;
import fr.expand.project.importdata.model.generated.DATAMODEL;
import fr.expand.project.importdata.model.generated.LINKTYPE;
import fr.expand.project.importdata.model.generated.OBJECTTYPE;
import fr.expand.project.importdata.model.generated.TYPEREF;
import fr.expand.project.importdata.util.CypherUtils;
import fr.expand.project.importdata.validation.AttributeValues;
import fr.expand.project.importdata.xml.XmlSupport;

import jakarta.xml.bind.JAXBException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns model definitions for one operation. Create a separate instance for every request; instances
 * and their mutable JAXB definitions are not shared across threads. The deprecated singleton is
 * retained for legacy clients only.
 */
public class ModelManager {

    private static final Logger LOGGER = LogManager.getLogger(ModelManager.class);

    private static ModelManager instance;

    private record ModelIdentity(String name, String version) {}

    private final Map<ModelIdentity, DATAMODEL> loadedModels;
    private final Map<ModelIdentity, String> loadedXml = new HashMap<>();
    private DATAMODEL currentModel;
    private String currentModelXml;
    private Map<String, OBJECTTYPE> objectTypeIndex;
    private Map<String, LINKTYPE> linkTypeIndex;

    public ModelManager() {
        loadedModels = new LinkedHashMap<>();
        objectTypeIndex = new HashMap<>();
        linkTypeIndex = new HashMap<>();
    }

    /** Get singleton instance */
    @Deprecated
    public static synchronized ModelManager getInstance() {
        if (instance == null) {
            instance = new ModelManager();
        }
        return instance;
    }

    /**
     * Load a model from XML file
     *
     * @param modelFile The XML file containing the model
     * @return The loaded data model
     * @throws JAXBException If parsing fails
     */
    public DATAMODEL loadModel(File modelFile) throws JAXBException {
        try {
            return loadModelFromXml(Files.readString(modelFile.toPath(), StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new JAXBException("Unable to read model XML", e);
        }
    }

    public DATAMODEL loadModelFromResource(String resourcePath) throws JAXBException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new JAXBException("Resource not found: " + resourcePath);
            }
            return loadModelFromXml(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new JAXBException("Unable to read model resource", e);
        }
    }

    /** Validate in isolation, then publish the complete candidate. Failed loads preserve state. */
    public DATAMODEL loadModelFromXml(String xmlContent) throws JAXBException {
        DATAMODEL model = XmlSupport.parseModel(xmlContent);
        ModelManager candidate = new ModelManager();
        candidate.currentModel = model;
        try {
            candidate.validateDefinitions(model);
            candidate.indexCurrentModel();
            candidate.validateInheritance(model);
            candidate.validateAttributeGroups(model);
            candidate.validateRepresentativeAttributes(model);
            candidate.validateLinkReferences(model);
        } catch (IllegalArgumentException e) {
            throw new JAXBException("Invalid model: " + e.getMessage(), e);
        }
        ModelIdentity identity = new ModelIdentity(model.getNAME(), model.getVERSION());
        loadedModels.put(identity, model);
        loadedXml.put(identity, xmlContent);
        currentModel = model;
        currentModelXml = xmlContent;
        objectTypeIndex = candidate.objectTypeIndex;
        linkTypeIndex = candidate.linkTypeIndex;
        LOGGER.info(
                "Model loaded successfully: "
                        + model.getNAME()
                        + " (version "
                        + model.getVERSION()
                        + ")");
        return model;
    }

    /**
     * Get the current active model
     *
     * @return The operation's JAXB definition, or null; treat it as read-only after loading
     */
    public DATAMODEL getCurrentModel() {
        return currentModel;
    }

    /** Return the raw XML for the current model, if available. */
    public String getCurrentModelXml() {
        return currentModelXml;
    }

    /**
     * Set the current active model
     *
     * @param modelName Name of the model to activate
     * @return true if model found and activated, false otherwise
     */
    public boolean setCurrentModel(String modelName) {
        ModelIdentity selected = null;
        for (ModelIdentity identity : loadedModels.keySet()) {
            if (identity.name().equals(modelName)) {
                selected = identity;
            }
        }
        return selected != null && activate(selected);
    }

    public boolean setCurrentModel(String modelName, String version) {
        return activate(new ModelIdentity(modelName, version));
    }

    private boolean activate(ModelIdentity identity) {
        DATAMODEL model = loadedModels.get(identity);
        if (model == null) {
            return false;
        }
        currentModel = model;
        currentModelXml = loadedXml.get(identity);
        indexCurrentModel();
        return true;
    }

    /**
     * Get a specific object type definition
     *
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
     *
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
     *
     * @param typeName Name of the object type
     * @return true if exists, false otherwise
     */
    public boolean objectTypeExists(String typeName) {
        return getObjectType(typeName) != null;
    }

    /**
     * Check if a link type exists in the current model
     *
     * @param typeName Name of the link type
     * @return true if exists, false otherwise
     */
    public boolean linkTypeExists(String typeName) {
        return getLinkType(typeName) != null;
    }

    /** Clear all loaded models */
    public void clearModels() {
        loadedModels.clear();
        loadedXml.clear();
        currentModel = null;
        currentModelXml = null;
        objectTypeIndex.clear();
        linkTypeIndex.clear();
        LOGGER.info("All models cleared");
    }

    /**
     * Get all loaded models
     *
     * @return A map copy of composite name/version keys to this operation's JAXB definitions
     */
    public Map<String, DATAMODEL> getLoadedModels() {
        Map<String, DATAMODEL> models = new LinkedHashMap<>();
        loadedModels.forEach(
                (identity, model) ->
                        models.put(
                                identity.name().length()
                                        + ":"
                                        + identity.name()
                                        + ":"
                                        + (identity.version() == null
                                                ? "-1:"
                                                : identity.version().length()
                                                        + ":"
                                                        + identity.version()),
                                model));
        return models;
    }

    /** Build indexes for object and link types for the current model. */
    private void indexCurrentModel() {
        objectTypeIndex.clear();
        linkTypeIndex.clear();

        if (currentModel == null) {
            return;
        }

        if (currentModel.getOBJECTTYPES() != null
                && currentModel.getOBJECTTYPES().getOBJECTTYPE() != null) {
            for (OBJECTTYPE objType : currentModel.getOBJECTTYPES().getOBJECTTYPE()) {
                objectTypeIndex.put(objType.getNAME(), objType);
            }
        }

        if (currentModel.getLINKTYPES() != null
                && currentModel.getLINKTYPES().getLINKTYPE() != null) {
            for (LINKTYPE linkType : currentModel.getLINKTYPES().getLINKTYPE()) {
                linkTypeIndex.put(linkType.getNAME(), linkType);
            }
        }
    }

    /** Resolve attribute definitions for a type, including inherited ones. */
    public Map<String, ATTRIBUTEDEFINITION> getAttributeDefinitionMap(OBJECTTYPE objectType) {
        Map<String, ATTRIBUTEDEFINITION> merged = new LinkedHashMap<>();
        if (objectType == null) {
            return merged;
        }

        for (OBJECTTYPE type : getHierarchyRootFirst(objectType)) {
            if (type.getATTRIBUTEDEFINITIONS() == null
                    || type.getATTRIBUTEDEFINITIONS().getATTRIBUTEDEFINITION() == null) {
                continue;
            }
            for (ATTRIBUTEDEFINITION def :
                    type.getATTRIBUTEDEFINITIONS().getATTRIBUTEDEFINITION()) {
                if (def.getNAME() == null) {
                    continue;
                }
                merged.put(def.getNAME(), def);
            }
        }

        return merged;
    }

    /** Check if a candidate type is the same as, or inherits from, an allowed type. */
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

    private static void requireName(String value, String context) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(context + " must not be blank");
        }
    }

    private static void requireTypeName(String value, String context) {
        requireName(value, context);
        try {
            CypherUtils.identifier(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    context + " must be a valid, unreserved graph identifier: " + value, e);
        }
    }

    private void validateDefinitions(DATAMODEL model) {
        requireName(model.getNAME(), "Model name");
        if (model.getVERSION() != null) {
            requireName(model.getVERSION(), "Model version");
        }
        Set<String> objectNames = new HashSet<>();
        for (OBJECTTYPE type : model.getOBJECTTYPES().getOBJECTTYPE()) {
            requireTypeName(type.getNAME(), "Object type name");
            if (!objectNames.add(type.getNAME())) {
                throw new IllegalArgumentException("Duplicate object type: " + type.getNAME());
            }
            if (type.getPARENT() != null) {
                requireTypeName(type.getPARENT(), "Parent type name");
            }
            if (type.getATTRIBUTEDEFINITIONS() != null) {
                validateAttributeDefinitions(
                        type.getATTRIBUTEDEFINITIONS().getATTRIBUTEDEFINITION(), type.getNAME());
            }
        }
        Set<String> linkNames = new HashSet<>();
        for (LINKTYPE type : model.getLINKTYPES().getLINKTYPE()) {
            requireTypeName(type.getNAME(), "Link type name");
            if (!linkNames.add(type.getNAME())) {
                throw new IllegalArgumentException("Duplicate link type: " + type.getNAME());
            }
            if (type.getATTRIBUTEDEFINITIONS() != null) {
                validateAttributeDefinitions(
                        type.getATTRIBUTEDEFINITIONS().getATTRIBUTEDEFINITION(), type.getNAME());
            }
        }
    }

    private static void validateAttributeDefinitions(
            List<ATTRIBUTEDEFINITION> definitions, String context) {
        Set<String> names = new HashSet<>();
        for (ATTRIBUTEDEFINITION definition : definitions) {
            String name = definition.getNAME();
            requireName(name, "Attribute name in " + context);
            if (!names.add(name)) {
                throw new IllegalArgumentException(
                        "Duplicate attribute definition: " + name + " in " + context);
            }
            if (AttributeValues.isReservedKey(name)) {
                throw new IllegalArgumentException("Reserved attribute name: " + name);
            }
            String value = definition.getDEFAULTVALUE();
            if (value != null
                    && ((definition.isREQUIRED() && value.isBlank())
                            || !AttributeValues.isValid(value, definition.getTYPE()))) {
                throw new IllegalArgumentException("Invalid default value for attribute: " + name);
            }
        }
    }

    private void validateLinkReferences(DATAMODEL model) {
        for (LINKTYPE type : model.getLINKTYPES().getLINKTYPE()) {
            List<TYPEREF> refs = new ArrayList<>(type.getSOURCETYPES().getTYPEREF());
            refs.addAll(type.getTARGETTYPES().getTYPEREF());
            for (TYPEREF ref : refs) {
                requireName(ref.getNAME(), "Link endpoint type name");
                if (!objectTypeIndex.containsKey(ref.getNAME())) {
                    throw new IllegalArgumentException(
                            "Unknown endpoint type '"
                                    + ref.getNAME()
                                    + "' for link type '"
                                    + type.getNAME()
                                    + "'");
                }
            }
        }
    }

    private void validateInheritance(DATAMODEL model) {
        if (model == null
                || model.getOBJECTTYPES() == null
                || model.getOBJECTTYPES().getOBJECTTYPE() == null) {
            return;
        }

        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();

        for (OBJECTTYPE objType : model.getOBJECTTYPES().getOBJECTTYPE()) {
            String parentName = objType.getPARENT();
            if (parentName != null
                    && !parentName.trim().isEmpty()
                    && !objectTypeIndex.containsKey(parentName)) {
                throw new IllegalArgumentException(
                        "Invalid inheritance: object type '"
                                + objType.getNAME()
                                + "' references unknown parent '"
                                + parentName
                                + "'");
            }

            String typeName = objType.getNAME();
            if (typeName != null && hasInheritanceCycle(typeName, visiting, visited)) {
                throw new IllegalArgumentException(
                        "Inheritance cycle detected involving type: " + typeName);
            }
        }
    }

    private void validateAttributeGroups(DATAMODEL model) {
        if (model == null
                || model.getOBJECTTYPES() == null
                || model.getOBJECTTYPES().getOBJECTTYPE() == null) {
            return;
        }

        for (OBJECTTYPE objectType : model.getOBJECTTYPES().getOBJECTTYPE()) {
            Map<String, ATTRIBUTEDEFINITION> definitions = getAttributeDefinitionMap(objectType);
            if (objectType.getATTRIBUTEGROUPS() == null
                    || objectType.getATTRIBUTEGROUPS().getATTRIBUTEGROUP() == null) {
                continue;
            }

            Set<String> groups = new HashSet<>();
            for (ATTRIBUTEGROUP group : objectType.getATTRIBUTEGROUPS().getATTRIBUTEGROUP()) {
                String groupName = group.getNAME();
                requireName(groupName, "Attribute group name");
                if (!groups.add(groupName)) {
                    throw new IllegalArgumentException("Duplicate attribute group: " + groupName);
                }
                if (group.getATTRIBUTEREF() == null || group.getATTRIBUTEREF().isEmpty()) {
                    LOGGER.warn(
                            "Empty attribute group '"
                                    + groupName
                                    + "' in type '"
                                    + objectType.getNAME()
                                    + "'");
                    continue;
                }

                Set<String> references = new HashSet<>();
                for (ATTRIBUTEREF ref : group.getATTRIBUTEREF()) {
                    String attributeName = ref.getNAME();
                    if (!references.add(attributeName)) {
                        throw new IllegalArgumentException(
                                "Duplicate attribute group reference: " + attributeName);
                    }
                    if (attributeName == null || attributeName.trim().isEmpty()) {
                        throw new IllegalArgumentException(
                                "Attribute group '"
                                        + groupName
                                        + "' in type '"
                                        + objectType.getNAME()
                                        + "' references an empty attribute name");
                    }
                    if (!definitions.containsKey(attributeName)) {
                        throw new IllegalArgumentException(
                                "Attribute group '"
                                        + groupName
                                        + "' in type '"
                                        + objectType.getNAME()
                                        + "' references unknown attribute '"
                                        + attributeName
                                        + "'");
                    }
                }
            }
        }
    }

    private void validateRepresentativeAttributes(DATAMODEL model) {
        if (model == null
                || model.getOBJECTTYPES() == null
                || model.getOBJECTTYPES().getOBJECTTYPE() == null) {
            return;
        }

        for (OBJECTTYPE objectType : model.getOBJECTTYPES().getOBJECTTYPE()) {
            Map<String, ATTRIBUTEDEFINITION> definitions = getAttributeDefinitionMap(objectType);
            if (objectType.getREPRESENTATIVEATTRIBUTES() == null
                    || objectType.getREPRESENTATIVEATTRIBUTES().getATTRIBUTEREF() == null) {
                continue;
            }

            List<ATTRIBUTEREF> refs = objectType.getREPRESENTATIVEATTRIBUTES().getATTRIBUTEREF();
            if (refs.isEmpty()) {
                LOGGER.warn(
                        "Empty representative attributes definition in type '"
                                + objectType.getNAME()
                                + "'");
                continue;
            }

            Set<String> references = new HashSet<>();
            for (ATTRIBUTEREF ref : refs) {
                String attributeName = ref.getNAME();
                if (!references.add(attributeName)) {
                    throw new IllegalArgumentException(
                            "Duplicate representative attribute: " + attributeName);
                }
                if (attributeName == null || attributeName.trim().isEmpty()) {
                    throw new IllegalArgumentException(
                            "Representative attributes in type '"
                                    + objectType.getNAME()
                                    + "' references an empty attribute name");
                }
                if (!definitions.containsKey(attributeName)) {
                    throw new IllegalArgumentException(
                            "Representative attributes in type '"
                                    + objectType.getNAME()
                                    + "' references unknown attribute '"
                                    + attributeName
                                    + "'");
                }
            }
        }
    }

    private boolean hasInheritanceCycle(
            String typeName, Set<String> visiting, Set<String> visited) {
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
            if (parentName != null
                    && !parentName.trim().isEmpty()
                    && objectTypeIndex.containsKey(parentName)) {
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
