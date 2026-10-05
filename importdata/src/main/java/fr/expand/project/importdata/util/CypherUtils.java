package fr.expand.project.importdata.util;

import fr.expand.project.importdata.dto.DataPackObject;
import fr.expand.project.importdata.dto.generated.ATTRIBUTE;

import java.util.*;

/** Values always travel as parameters; only validated identifiers enter query text. */
public final class CypherUtils {
    private static final Set<String> RESERVED =
            Set.of(
                    "modelkey",
                    "dataid",
                    "type",
                    "id",
                    "uuid",
                    "_uuid",
                    "linktype",
                    "directed",
                    "elementid",
                    "internalid",
                    "searchtext");
    private static final Set<String> RESERVED_TYPES =
            Set.of(
                    "dataobject",
                    "datamodel",
                    "modelobjecttype",
                    "modellinktype",
                    "modelattribute",
                    "auditdata");

    private CypherUtils() {}

    public static boolean isReservedType(String name) {
        return name != null && RESERVED_TYPES.contains(name.toLowerCase(Locale.ROOT));
    }

    public static String identifier(String name) {
        if (name == null || isReservedType(name) || !name.matches("[A-Za-z_][A-Za-z0-9_]*"))
            throw new IllegalArgumentException("Invalid graph type name: " + name);
        return "`" + name + "`";
    }

    public static boolean isReservedProperty(String key) {
        return key != null && RESERVED.contains(key.toLowerCase(Locale.ROOT));
    }

    public static void validateAttributeKey(String key) {
        if (key == null || key.isBlank() || isReservedProperty(key))
            throw new IllegalArgumentException("Invalid or reserved attribute key: " + key);
    }

    public static Map<String, Object> attributes(List<ATTRIBUTE> attributes) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (ATTRIBUTE attribute : attributes) {
            validateAttributeKey(attribute.getKEY());
            if (props.putIfAbsent(
                            attribute.getKEY(),
                            attribute.getVALUE() == null ? "" : attribute.getVALUE())
                    != null)
                throw new IllegalArgumentException("Duplicate attribute: " + attribute.getKEY());
        }
        return props;
    }

    public static Map<String, Object> properties(DataPackObject object, String modelKey) {
        identifier(object.getTYPE());
        Map<String, Object> props = attributes(object.getATTRIBUTE());
        props.put("dataId", object.getID());
        props.put("type", object.getTYPE());
        props.put("uuid", UUID.randomUUID().toString());
        if (modelKey != null && !modelKey.isBlank()) props.put("modelKey", modelKey);
        return props;
    }

    /** Compatibility query fragment, paired with the properties parameter. */
    public static String convertObjectForDb(DataPackObject object) {
        return convertObjectForDb(object, null);
    }

    public static String convertObjectForDb(DataPackObject object, String modelKey) {
        return identifier(object.getTYPE())
                + (modelKey == null || modelKey.isBlank() ? "" : ":DataObject")
                + " $properties";
    }

    public static String convertObjectForDbSubtitution(DataPackObject object) {
        return convertObjectForDb(object);
    }

    public static String convertObjectForDbSubtitution(DataPackObject object, String modelKey) {
        return convertObjectForDb(object, modelKey);
    }
}
