package fr.expand.project.importdata.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import fr.expand.project.importdata.dto.DataPackAttribute;
import fr.expand.project.importdata.dto.generated.ATTRIBUTE;
import fr.expand.project.importdata.dto.generated.DATAS;
import fr.expand.project.importdata.dto.generated.LINK;
import fr.expand.project.importdata.dto.generated.OBJECT;
import fr.expand.project.importdata.model.ModelManager;
import fr.expand.project.importdata.model.generated.ATTRIBUTEDEFINITION;
import fr.expand.project.importdata.model.generated.ATTRIBUTETYPE;
import fr.expand.project.importdata.model.generated.DATAMODEL;
import fr.expand.project.importdata.model.generated.LINKTYPE;
import fr.expand.project.importdata.model.generated.OBJECTTYPE;
import fr.expand.project.importdata.model.generated.TYPEREF;

/**
 * Validator for data against model definitions
 */
public class DataValidator {
    
    private static final Logger LOGGER = LogManager.getLogger(DataValidator.class);
    
    private ModelManager modelManager;
    private List<ValidationError> errors;
    private List<ValidationWarning> warnings;
    
    public DataValidator() {
        this.modelManager = ModelManager.getInstance();
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }
    
    /**
     * Validate data against the current model
     * @param data Data to validate
     * @return ValidationResult containing errors and warnings
     */
    public ValidationResult validate(DATAS data) {
        errors.clear();
        warnings.clear();
        
        if (!ensureModelLoaded()) {
            return new ValidationResult(errors, warnings);
        }
        
        DATAMODEL model = modelManager.getCurrentModel();
        LOGGER.info("Starting validation against model: " + model.getNAME());
        
        // Validate objects
        if (data.getOBJECTS() != null && data.getOBJECTS().getOBJECT() != null) {
            validateObjects(data.getOBJECTS().getOBJECT());
        }
        
        // Validate links
        if (data.getLINKS() != null && data.getLINKS().getLINK() != null) {
            List<OBJECT> objects = Collections.emptyList();
            if (data.getOBJECTS() != null && data.getOBJECTS().getOBJECT() != null) {
                objects = data.getOBJECTS().getOBJECT();
            }
            validateLinks(data.getLINKS().getLINK(), objects);
        }
        
        LOGGER.info("Validation completed. Errors: " + errors.size() + ", Warnings: " + warnings.size());
        
        return new ValidationResult(errors, warnings);
    }

    /**
     * Validate a single link against the current model and a list of available objects.
     * This is useful for API flows where the source and target objects already exist.
     */
    public ValidationResult validateLink(LINK link, List<OBJECT> availableObjects) {
        errors.clear();
        warnings.clear();

        if (!ensureModelLoaded()) {
            return new ValidationResult(errors, warnings);
        }

        if (link == null) {
            errors.add(new ValidationError("LINK", "Link payload is missing"));
            return new ValidationResult(errors, warnings);
        }

        validateLinks(List.of(link), availableObjects == null ? Collections.emptyList() : availableObjects);
        return new ValidationResult(errors, warnings);
    }
    
    /**
     * Validate all objects
     */
    private void validateObjects(List<OBJECT> objects) {
        Set<Integer> objectIds = new HashSet<>();
        
        for (OBJECT obj : objects) {
            // Check for duplicate IDs
            if (objectIds.contains(obj.getID())) {
                errors.add(new ValidationError("OBJECT", "Duplicate object ID: " + obj.getID()));
            }
            objectIds.add(obj.getID());
            
            // Validate object type exists
            String typeName = obj.getTYPE();
            OBJECTTYPE objectType = modelManager.getObjectType(typeName);
            
            if (objectType == null) {
                errors.add(new ValidationError("OBJECT[" + obj.getID() + "]", 
                    "Unknown object type: " + typeName));
                continue;
            }
            
            // Validate attributes
            validateObjectAttributes(obj, objectType);
        }
    }
    
    /**
     * Validate object attributes against type definition
     */
    private void validateObjectAttributes(OBJECT obj, OBJECTTYPE objectType) {
        Map<String, ATTRIBUTEDEFINITION> attrDefinitions = modelManager.getAttributeDefinitionMap(objectType);
        if (attrDefinitions.isEmpty()) {
            return;
        }

        Set<String> providedAttributes = new HashSet<>();
        for (ATTRIBUTE attr : obj.getATTRIBUTE()) {
            providedAttributes.add(attr.getKEY());
        }
        
        // Check required attributes
        for (ATTRIBUTEDEFINITION attrDef : attrDefinitions.values()) {
            String attrName = attrDef.getNAME();
            
            if (attrDef.isREQUIRED() && !providedAttributes.contains(attrName)) {
                errors.add(new ValidationError("OBJECT[" + obj.getID() + "]", 
                    "Missing required attribute: " + attrName));
            }
        }
        
        // Validate attribute types
        for (ATTRIBUTE attr : obj.getATTRIBUTE()) {
            validateAttributeType(obj, attr, attrDefinitions);
        }
    }
    
    /**
     * Validate attribute value type
     */
    private void validateAttributeType(OBJECT obj, ATTRIBUTE attr, Map<String, ATTRIBUTEDEFINITION> attrDefinitions) {
        if (attrDefinitions == null || attrDefinitions.isEmpty()) {
            warnings.add(new ValidationWarning("OBJECT[" + obj.getID() + "]", 
                "Attribute '" + attr.getKEY() + "' not defined in model (no attribute definitions)"));
            return;
        }
        
        ATTRIBUTEDEFINITION attrDef = attrDefinitions.get(attr.getKEY());
        
        if (attrDef == null) {
            warnings.add(new ValidationWarning("OBJECT[" + obj.getID() + "]", 
                "Attribute '" + attr.getKEY() + "' not defined in model"));
            return;
        }
        
        // Validate type if value is present
        if (attr.getVALUE() != null && !attr.getVALUE().isEmpty()) {
            ATTRIBUTETYPE type = attrDef.getTYPE();
            if (type != null && !validateValueType(attr.getVALUE(), type)) {
                errors.add(new ValidationError("OBJECT[" + obj.getID() + "]", 
                    "Invalid type for attribute '" + attr.getKEY() + "': expected " + type + ", got '" + attr.getVALUE() + "'"));
            }
        }
    }
    
    /**
     * Validate value matches expected type
     */
    private boolean validateValueType(String value, ATTRIBUTETYPE type) {
        try {
            switch (type) {
                case INTEGER:
                    Integer.parseInt(value);
                    return true;
                case DOUBLE:
                    Double.parseDouble(value);
                    return true;
                case BOOLEAN:
                    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                        return false;
                    }
                    return true;
                case DATE:
                    // Basic date format validation (simplified)
                    return value.matches("\\d{4}-\\d{2}-\\d{2}.*");
                case STRING:
                default:
                    return true;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Validate all links
     */
    private void validateLinks(List<LINK> links, List<OBJECT> objects) {
        // Build object lookup
        Set<String> objectKeys = new HashSet<>();
        for (OBJECT obj : objects) {
            if (obj == null) {
                continue;
            }
            objectKeys.add(buildObjectKey(obj.getID(), obj.getTYPE()));
        }
        
        for (int index = 0; index < links.size(); index++) {
            LINK link = links.get(index);
            String linkContext = buildLinkContext(index, link);

            if (link == null) {
                errors.add(new ValidationError("LINK[" + (index + 1) + "]", "Link entry is null"));
                continue;
            }

            if (link.getOBJLINKA() == null || link.getOBJLINKB() == null) {
                errors.add(new ValidationError(linkContext, "Link endpoints are missing"));
                continue;
            }

            // Validate link type exists
            String linkTypeName = normalizeName(link.getTYPE());
            LINKTYPE linkType = modelManager.getLinkType(linkTypeName);
            
            if (linkType == null) {
                errors.add(new ValidationError(linkContext,
                    "Unknown link type '" + displayValue(linkTypeName) + "'"));
                continue;
            }
            
            // Validate source and target objects exist
            String sourceKey = buildObjectKey(link.getOBJLINKA().getID(), link.getOBJLINKA().getTYPE());
            String targetKey = buildObjectKey(link.getOBJLINKB().getID(), link.getOBJLINKB().getTYPE());
            
            if (!objectKeys.contains(sourceKey)) {
                errors.add(new ValidationError(linkContext,
                    "Source object not found in payload: " + sourceKey));
            }
            
            if (!objectKeys.contains(targetKey)) {
                errors.add(new ValidationError(linkContext,
                    "Target object not found in payload: " + targetKey));
            }
            
            // Validate source and target types are allowed
            validateLinkTypes(link, linkType, linkContext);
            validateLinkAttributes(link, linkType, linkContext);
        }
    }
    
    /**
     * Validate link source and target types
     */
    private void validateLinkTypes(LINK link, LINKTYPE linkType, String linkContext) {
        String sourceType = normalizeName(link.getOBJLINKA().getTYPE());
        String targetType = normalizeName(link.getOBJLINKB().getTYPE());
        
        // Check source type is allowed
        boolean sourceAllowed = false;
        List<TYPEREF> allowedSourceTypes = Collections.emptyList();
        if (linkType.getSOURCETYPES() != null) {
            allowedSourceTypes = linkType.getSOURCETYPES().getTYPEREF();
            for (TYPEREF typeRef : allowedSourceTypes) {
                if (modelManager.isTypeOrSubtype(sourceType, typeRef.getNAME())) {
                    sourceAllowed = true;
                    break;
                }
            }
        }
        
        if (!sourceAllowed) {
            errors.add(new ValidationError(linkContext,
                "Source type '" + displayValue(sourceType) + "' is not allowed for link type '"
                    + displayValue(linkType.getNAME()) + "'. Allowed source types: "
                    + formatAllowedTypes(allowedSourceTypes)));
        }
        
        // Check target type is allowed
        boolean targetAllowed = false;
        List<TYPEREF> allowedTargetTypes = Collections.emptyList();
        if (linkType.getTARGETTYPES() != null) {
            allowedTargetTypes = linkType.getTARGETTYPES().getTYPEREF();
            for (TYPEREF typeRef : allowedTargetTypes) {
                if (modelManager.isTypeOrSubtype(targetType, typeRef.getNAME())) {
                    targetAllowed = true;
                    break;
                }
            }
        }
        
        if (!targetAllowed) {
            errors.add(new ValidationError(linkContext,
                "Target type '" + displayValue(targetType) + "' is not allowed for link type '"
                    + displayValue(linkType.getNAME()) + "'. Allowed target types: "
                    + formatAllowedTypes(allowedTargetTypes)));
        }
    }

    private void validateLinkAttributes(LINK link, LINKTYPE linkType, String linkContext) {
        Map<String, ATTRIBUTEDEFINITION> attrDefinitions = getLinkAttributeDefinitions(linkType);
        Set<String> providedAttributes = new HashSet<>();

        for (ATTRIBUTE attr : link.getATTRIBUTE()) {
            if (attr == null) {
                continue;
            }
            String attrName = normalizeName(attr.getKEY());
            if (attrName == null) {
                errors.add(new ValidationError(linkContext, "Attribute name is empty"));
                continue;
            }
            if (!providedAttributes.add(attrName)) {
                errors.add(new ValidationError(linkContext,
                    "Duplicate attribute '" + attrName + "' on link type '" + displayValue(linkType.getNAME()) + "'"));
            }
        }

        applyDefaultLinkAttributes(link, attrDefinitions, providedAttributes);

        for (ATTRIBUTEDEFINITION attrDef : attrDefinitions.values()) {
            String attrName = attrDef.getNAME();
            if (Boolean.TRUE.equals(attrDef.isREQUIRED()) && !hasNonBlankAttributeValue(link, attrName)) {
                errors.add(new ValidationError(linkContext,
                    "Missing required attribute '" + attrName + "' for link type '"
                        + displayValue(linkType.getNAME()) + "'"));
            }
        }

        for (ATTRIBUTE attr : link.getATTRIBUTE()) {
            if (attr == null) {
                continue;
            }
            String attrName = normalizeName(attr.getKEY());
            if (attrName == null) {
                continue;
            }

            ATTRIBUTEDEFINITION attrDef = attrDefinitions.get(attrName);
            if (attrDef == null) {
                errors.add(new ValidationError(linkContext,
                    "Unknown attribute '" + attrName + "' for link type '"
                        + displayValue(linkType.getNAME()) + "'. Allowed attributes: "
                        + formatAllowedAttributes(attrDefinitions)));
                continue;
            }

            String value = attr.getVALUE();
            if (value == null || value.isBlank()) {
                continue;
            }

            ATTRIBUTETYPE type = attrDef.getTYPE();
            if (type != null && !validateValueType(value, type)) {
                errors.add(new ValidationError(linkContext,
                    "Invalid value '" + value + "' for attribute '" + attrName
                        + "' on link type '" + displayValue(linkType.getNAME())
                        + "': expected " + type));
            }
        }
    }

    private void applyDefaultLinkAttributes(
        LINK link,
        Map<String, ATTRIBUTEDEFINITION> attrDefinitions,
        Set<String> providedAttributes
    ) {
        for (ATTRIBUTEDEFINITION attrDef : attrDefinitions.values()) {
            String attrName = attrDef.getNAME();
            String defaultValue = attrDef.getDEFAULTVALUE();
            if (attrName == null || providedAttributes.contains(attrName) || defaultValue == null) {
                continue;
            }
            link.getATTRIBUTE().add(new DataPackAttribute(attrName, defaultValue));
            providedAttributes.add(attrName);
        }
    }

    private boolean hasNonBlankAttributeValue(LINK link, String attrName) {
        for (ATTRIBUTE attr : link.getATTRIBUTE()) {
            if (attr == null) {
                continue;
            }
            String key = normalizeName(attr.getKEY());
            if (attrName != null && attrName.equals(key)) {
                return attr.getVALUE() != null && !attr.getVALUE().isBlank();
            }
        }
        return false;
    }

    private Map<String, ATTRIBUTEDEFINITION> getLinkAttributeDefinitions(LINKTYPE linkType) {
        Map<String, ATTRIBUTEDEFINITION> definitions = new LinkedHashMap<>();
        if (linkType == null
            || linkType.getATTRIBUTEDEFINITIONS() == null
            || linkType.getATTRIBUTEDEFINITIONS().getATTRIBUTEDEFINITION() == null) {
            return definitions;
        }

        for (ATTRIBUTEDEFINITION attrDef : linkType.getATTRIBUTEDEFINITIONS().getATTRIBUTEDEFINITION()) {
            if (attrDef == null || attrDef.getNAME() == null) {
                continue;
            }
            definitions.put(attrDef.getNAME(), attrDef);
        }
        return definitions;
    }

    private boolean ensureModelLoaded() {
        DATAMODEL model = modelManager.getCurrentModel();
        if (model == null) {
            errors.add(new ValidationError("GLOBAL", "No model loaded"));
            return false;
        }
        return true;
    }

    private String buildLinkContext(int index, LINK link) {
        if (link == null) {
            return "LINK[" + (index + 1) + "]";
        }

        String typeName = normalizeName(link.getTYPE());
        String sourceKey = "?";
        String targetKey = "?";

        if (link.getOBJLINKA() != null) {
            sourceKey = buildObjectKey(link.getOBJLINKA().getID(), link.getOBJLINKA().getTYPE());
        }
        if (link.getOBJLINKB() != null) {
            targetKey = buildObjectKey(link.getOBJLINKB().getID(), link.getOBJLINKB().getTYPE());
        }

        return "LINK[" + (index + 1) + " " + displayValue(typeName) + " " + sourceKey + " -> " + targetKey + "]";
    }

    private String buildObjectKey(int id, String type) {
        return id + ":" + displayValue(normalizeName(type));
    }

    private String formatAllowedTypes(List<TYPEREF> refs) {
        if (refs == null || refs.isEmpty()) {
            return "<none>";
        }

        List<String> names = new ArrayList<>();
        for (TYPEREF ref : refs) {
            if (ref != null && ref.getNAME() != null && !ref.getNAME().isBlank()) {
                names.add(ref.getNAME());
            }
        }
        return names.isEmpty() ? "<none>" : String.join(", ", names);
    }

    private String formatAllowedAttributes(Map<String, ATTRIBUTEDEFINITION> attrDefinitions) {
        if (attrDefinitions == null || attrDefinitions.isEmpty()) {
            return "<none>";
        }
        return String.join(", ", attrDefinitions.keySet());
    }

    private String normalizeName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String displayValue(String value) {
        return value == null || value.isBlank() ? "<empty>" : value;
    }
}
