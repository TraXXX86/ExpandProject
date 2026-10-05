package fr.expand.project.importdata.validation;

import fr.expand.project.importdata.dto.generated.ATTRIBUTE;
import fr.expand.project.importdata.dto.generated.DATAS;
import fr.expand.project.importdata.dto.generated.LINK;
import fr.expand.project.importdata.dto.generated.OBJECT;
import fr.expand.project.importdata.model.ModelManager;
import fr.expand.project.importdata.model.generated.ATTRIBUTEDEFINITION;
import fr.expand.project.importdata.model.generated.LINKTYPE;
import fr.expand.project.importdata.model.generated.OBJECTTYPE;
import fr.expand.project.importdata.model.generated.TYPEREF;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Validates a data pack using one explicitly selected model. */
public class DataValidator {
    private final ModelManager modelManager;
    private final List<ValidationError> errors = new ArrayList<>();
    private final List<ValidationWarning> warnings = new ArrayList<>();

    /** Compatibility constructor for existing CLI clients. */
    public DataValidator() {
        this(ModelManager.getInstance());
    }

    public DataValidator(ModelManager modelManager) {
        this.modelManager = Objects.requireNonNull(modelManager, "modelManager");
    }

    /**
     * Missing defaults are appended to the submitted DTO, so validation and storage use identical
     * effective values. A supplied blank value never invokes a default.
     */
    public synchronized ValidationResult validate(DATAS data) {
        errors.clear();
        warnings.clear();
        if (modelManager.getCurrentModel() == null) {
            error("GLOBAL", "No model loaded");
            return result();
        }
        if (data == null) {
            error("GLOBAL", "Data pack is missing");
            return result();
        }
        List<OBJECT> objects =
                data.getOBJECTS() == null ? Collections.emptyList() : data.getOBJECTS().getOBJECT();
        validateObjects(objects);
        if (data.getLINKS() != null) {
            validateLinks(data.getLINKS().getLINK(), objects);
        }
        return result();
    }

    /** Validate one object's complete attributes, including inherited definitions/defaults. */
    public synchronized ValidationResult validateObjectAttributes(OBJECT object) {
        errors.clear();
        warnings.clear();
        if (modelManager.getCurrentModel() == null) {
            error("GLOBAL", "No model loaded");
        } else if (object == null) {
            error("OBJECT", "Object is missing");
        } else {
            OBJECTTYPE type = modelManager.getObjectType(object.getTYPE());
            String context = "OBJECT[" + object.getID() + "]";
            if (type == null) {
                error(context, "Unknown object type: " + object.getTYPE());
            } else {
                validateAttributes(
                        context,
                        object.getATTRIBUTE(),
                        modelManager.getAttributeDefinitionMap(type));
            }
        }
        return result();
    }

    /** Validate one link's complete attributes and append defaults; does not check endpoints. */
    public synchronized ValidationResult validateLinkAttributes(LINK link) {
        errors.clear();
        warnings.clear();
        if (modelManager.getCurrentModel() == null) {
            error("GLOBAL", "No model loaded");
        } else if (link == null) {
            error("LINK", "Link is missing");
        } else {
            LINKTYPE type = modelManager.getLinkType(link.getTYPE());
            String context = "LINK[" + link.getTYPE() + "]";
            if (type == null) {
                error(context, "Unknown link type: " + link.getTYPE());
            } else {
                validateAttributes(context, link.getATTRIBUTE(), linkDefinitions(type));
            }
        }
        return result();
    }

    private Map<String, ATTRIBUTEDEFINITION> linkDefinitions(LINKTYPE type) {
        Map<String, ATTRIBUTEDEFINITION> definitions = new LinkedHashMap<>();
        if (type.getATTRIBUTEDEFINITIONS() != null) {
            for (ATTRIBUTEDEFINITION definition :
                    type.getATTRIBUTEDEFINITIONS().getATTRIBUTEDEFINITION()) {
                definitions.put(definition.getNAME(), definition);
            }
        }
        return definitions;
    }

    private ValidationResult result() {
        return new ValidationResult(errors, warnings);
    }

    private void error(String context, String message) {
        errors.add(new ValidationError(context, message));
    }

    private static String objectKey(int id, String type) {
        return type + ":" + id;
    }

    private void validateObjects(List<OBJECT> objects) {
        Set<String> objectIds = new HashSet<>();
        for (OBJECT object : objects) {
            if (object == null) {
                error("OBJECT", "Object is missing");
                continue;
            }
            String context = "OBJECT[" + object.getID() + "]";
            if (!objectIds.add(objectKey(object.getID(), object.getTYPE()))) {
                error(
                        context,
                        "Duplicate object ID: " + object.getID() + " for type " + object.getTYPE());
            }
            OBJECTTYPE type = modelManager.getObjectType(object.getTYPE());
            if (type == null) {
                error(context, "Unknown object type: " + object.getTYPE());
                continue;
            }
            validateAttributes(
                    context, object.getATTRIBUTE(), modelManager.getAttributeDefinitionMap(type));
        }
    }

    private void validateAttributes(
            String context,
            List<ATTRIBUTE> attributes,
            Map<String, ATTRIBUTEDEFINITION> definitions) {
        Set<String> provided = new HashSet<>();
        for (ATTRIBUTE attribute : attributes) {
            if (attribute == null || attribute.getKEY() == null || attribute.getKEY().isBlank()) {
                error(context, "Attribute key must not be blank");
                continue;
            }
            String key = attribute.getKEY();
            if (!provided.add(key)) {
                error(context, "Duplicate attribute: " + key);
            }
            if (AttributeValues.isReservedKey(key)) {
                error(context, "Reserved attribute key: " + key);
            }
        }
        for (ATTRIBUTEDEFINITION definition : definitions.values()) {
            String key = definition.getNAME();
            if (!provided.contains(key) && definition.getDEFAULTVALUE() != null) {
                ATTRIBUTE attribute = new ATTRIBUTE();
                attribute.setKEY(key);
                attribute.setVALUE(definition.getDEFAULTVALUE());
                attributes.add(attribute);
                provided.add(key);
            }
            if (definition.isREQUIRED() && !provided.contains(key)) {
                error(context, "Missing required attribute: " + key);
            }
        }
        for (ATTRIBUTE attribute : attributes) {
            if (attribute == null || attribute.getKEY() == null || attribute.getKEY().isBlank()) {
                continue;
            }
            ATTRIBUTEDEFINITION definition = definitions.get(attribute.getKEY());
            if (definition == null) {
                warnings.add(
                        new ValidationWarning(
                                context,
                                "Attribute '" + attribute.getKEY() + "' not defined in model"));
                continue;
            }
            String value = attribute.getVALUE();
            if (definition.isREQUIRED() && (value == null || value.isBlank())) {
                error(context, "Required attribute must not be blank: " + attribute.getKEY());
            } else if (!AttributeValues.isValid(value, definition.getTYPE())) {
                error(
                        context,
                        "Invalid type for attribute '"
                                + attribute.getKEY()
                                + "': expected "
                                + definition.getTYPE()
                                + ", got '"
                                + value
                                + "'");
            }
        }
    }

    private void validateLinks(List<LINK> links, List<OBJECT> objects) {
        Set<String> objectKeys = new HashSet<>();
        for (OBJECT object : objects) {
            if (object != null) {
                objectKeys.add(objectKey(object.getID(), object.getTYPE()));
            }
        }
        for (LINK link : links) {
            if (link == null) {
                error("LINK", "Link is missing");
                continue;
            }
            String context = "LINK[" + link.getTYPE() + "]";
            LINKTYPE type = modelManager.getLinkType(link.getTYPE());
            if (type == null) {
                error(context, "Unknown link type: " + link.getTYPE());
                continue;
            }
            validateAttributes(context, link.getATTRIBUTE(), linkDefinitions(type));
            if (link.getOBJLINKA() == null || link.getOBJLINKB() == null) {
                if (link.getOBJLINKA() == null) {
                    error(context, "Source object reference is missing");
                }
                if (link.getOBJLINKB() == null) {
                    error(context, "Target object reference is missing");
                }
                continue;
            }
            String sourceType = link.getOBJLINKA().getTYPE();
            String targetType = link.getOBJLINKB().getTYPE();
            String sourceKey = objectKey(link.getOBJLINKA().getID(), sourceType);
            String targetKey = objectKey(link.getOBJLINKB().getID(), targetType);
            if (!objectKeys.contains(sourceKey)) {
                error(context, "Source object not found: " + sourceKey);
            }
            if (!objectKeys.contains(targetKey)) {
                error(context, "Target object not found: " + targetKey);
            }
            if (!isAllowed(sourceType, type.getSOURCETYPES().getTYPEREF())) {
                error(context, "Source type '" + sourceType + "' not allowed for this link type");
            }
            if (!isAllowed(targetType, type.getTARGETTYPES().getTYPEREF())) {
                error(context, "Target type '" + targetType + "' not allowed for this link type");
            }
        }
    }

    private boolean isAllowed(String candidate, List<TYPEREF> allowed) {
        for (TYPEREF ref : allowed) {
            if (modelManager.isTypeOrSubtype(candidate, ref.getNAME())) {
                return true;
            }
        }
        return false;
    }
}
