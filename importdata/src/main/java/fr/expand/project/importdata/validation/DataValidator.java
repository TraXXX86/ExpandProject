package fr.expand.project.importdata.validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
        
        DATAMODEL model = modelManager.getCurrentModel();
        if (model == null) {
            errors.add(new ValidationError("GLOBAL", "No model loaded"));
            return new ValidationResult(errors, warnings);
        }
        
        LOGGER.info("Starting validation against model: " + model.getNAME());
        
        // Validate objects
        if (data.getOBJECTS() != null && data.getOBJECTS().getOBJECT() != null) {
            validateObjects(data.getOBJECTS().getOBJECT());
        }
        
        // Validate links
        if (data.getLINKS() != null && data.getLINKS().getLINK() != null) {
            validateLinks(data.getLINKS().getLINK(), data.getOBJECTS().getOBJECT());
        }
        
        LOGGER.info("Validation completed. Errors: " + errors.size() + ", Warnings: " + warnings.size());
        
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
            objectKeys.add(obj.getID() + ":" + obj.getTYPE());
        }
        
        for (LINK link : links) {
            // Validate link type exists
            String linkTypeName = link.getTYPE();
            LINKTYPE linkType = modelManager.getLinkType(linkTypeName);
            
            if (linkType == null) {
                errors.add(new ValidationError("LINK", "Unknown link type: " + linkTypeName));
                continue;
            }
            
            // Validate source and target objects exist
            String sourceKey = link.getOBJLINKA().getID() + ":" + link.getOBJLINKA().getTYPE();
            String targetKey = link.getOBJLINKB().getID() + ":" + link.getOBJLINKB().getTYPE();
            
            if (!objectKeys.contains(sourceKey)) {
                errors.add(new ValidationError("LINK[" + linkTypeName + "]", 
                    "Source object not found: " + sourceKey));
            }
            
            if (!objectKeys.contains(targetKey)) {
                errors.add(new ValidationError("LINK[" + linkTypeName + "]", 
                    "Target object not found: " + targetKey));
            }
            
            // Validate source and target types are allowed
            validateLinkTypes(link, linkType);
        }
    }
    
    /**
     * Validate link source and target types
     */
    private void validateLinkTypes(LINK link, LINKTYPE linkType) {
        String sourceType = link.getOBJLINKA().getTYPE();
        String targetType = link.getOBJLINKB().getTYPE();
        
        // Check source type is allowed
        boolean sourceAllowed = false;
        if (linkType.getSOURCETYPES() != null) {
            for (TYPEREF typeRef : linkType.getSOURCETYPES().getTYPEREF()) {
                if (modelManager.isTypeOrSubtype(sourceType, typeRef.getNAME())) {
                    sourceAllowed = true;
                    break;
                }
            }
        }
        
        if (!sourceAllowed) {
            errors.add(new ValidationError("LINK[" + link.getTYPE() + "]", 
                "Source type '" + sourceType + "' not allowed for this link type"));
        }
        
        // Check target type is allowed
        boolean targetAllowed = false;
        if (linkType.getTARGETTYPES() != null) {
            for (TYPEREF typeRef : linkType.getTARGETTYPES().getTYPEREF()) {
                if (modelManager.isTypeOrSubtype(targetType, typeRef.getNAME())) {
                    targetAllowed = true;
                    break;
                }
            }
        }
        
        if (!targetAllowed) {
            errors.add(new ValidationError("LINK[" + link.getTYPE() + "]", 
                "Target type '" + targetType + "' not allowed for this link type"));
        }
    }
}
