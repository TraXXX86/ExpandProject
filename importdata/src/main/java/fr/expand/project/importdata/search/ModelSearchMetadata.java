package fr.expand.project.importdata.search;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import fr.expand.project.importdata.model.ModelManager;
import fr.expand.project.importdata.model.generated.ATTRIBUTEDEFINITION;
import fr.expand.project.importdata.model.generated.ATTRIBUTEREF;
import fr.expand.project.importdata.model.generated.DATAMODEL;
import fr.expand.project.importdata.model.generated.OBJECTTYPE;

public class ModelSearchMetadata {

    private final Map<String, List<String>> searchableAttributesByType;
    private final Map<String, List<String>> representativeAttributesByType;

    private ModelSearchMetadata(
        Map<String, List<String>> searchableAttributesByType,
        Map<String, List<String>> representativeAttributesByType
    ) {
        this.searchableAttributesByType = searchableAttributesByType;
        this.representativeAttributesByType = representativeAttributesByType;
    }

    public static ModelSearchMetadata from(ModelManager modelManager) {
        Map<String, List<String>> searchable = new LinkedHashMap<>();
        Map<String, List<String>> representative = new LinkedHashMap<>();

        if (modelManager == null) {
            return new ModelSearchMetadata(searchable, representative);
        }

        DATAMODEL model = modelManager.getCurrentModel();
        if (model == null
            || model.getOBJECTTYPES() == null
            || model.getOBJECTTYPES().getOBJECTTYPE() == null) {
            return new ModelSearchMetadata(searchable, representative);
        }

        for (OBJECTTYPE objectType : model.getOBJECTTYPES().getOBJECTTYPE()) {
            if (objectType == null || objectType.getNAME() == null || objectType.getNAME().isBlank()) {
                continue;
            }
            searchable.put(objectType.getNAME(), resolveSearchableAttributes(modelManager, objectType));
            representative.put(objectType.getNAME(), resolveRepresentativeAttributes(modelManager, objectType));
        }

        return new ModelSearchMetadata(searchable, representative);
    }

    public Map<String, List<String>> getSearchableAttributesByType() {
        return copy(searchableAttributesByType);
    }

    public Map<String, List<String>> getRepresentativeAttributesByType() {
        return copy(representativeAttributesByType);
    }

    private static List<String> resolveSearchableAttributes(ModelManager modelManager, OBJECTTYPE objectType) {
        List<String> searchable = new ArrayList<>();
        Map<String, ATTRIBUTEDEFINITION> definitions = modelManager.getAttributeDefinitionMap(objectType);
        for (Map.Entry<String, ATTRIBUTEDEFINITION> entry : definitions.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            ATTRIBUTEDEFINITION definition = entry.getValue();
            if (definition != null && Boolean.TRUE.equals(definition.isSEARCHABLE())) {
                searchable.add(entry.getKey());
            }
        }
        return searchable;
    }

    private static List<String> resolveRepresentativeAttributes(ModelManager modelManager, OBJECTTYPE objectType) {
        List<String> resolved = new ArrayList<>();
        if (modelManager == null || objectType == null) {
            return resolved;
        }

        List<OBJECTTYPE> hierarchy = new ArrayList<>();
        OBJECTTYPE current = objectType;
        List<String> visited = new ArrayList<>();
        while (current != null && current.getNAME() != null && !visited.contains(current.getNAME())) {
            visited.add(current.getNAME());
            hierarchy.add(0, current);
            String parentName = current.getPARENT();
            if (parentName == null || parentName.isBlank()) {
                break;
            }
            current = modelManager.getObjectType(parentName);
        }

        for (OBJECTTYPE candidate : hierarchy) {
            List<String> currentRefs = extractRepresentativeAttributes(candidate);
            if (!currentRefs.isEmpty()) {
                resolved = currentRefs;
            }
        }
        return resolved;
    }

    private static List<String> extractRepresentativeAttributes(OBJECTTYPE objectType) {
        List<String> refs = new ArrayList<>();
        if (objectType == null
            || objectType.getREPRESENTATIVEATTRIBUTES() == null
            || objectType.getREPRESENTATIVEATTRIBUTES().getATTRIBUTEREF() == null) {
            return refs;
        }

        for (ATTRIBUTEREF attributeRef : objectType.getREPRESENTATIVEATTRIBUTES().getATTRIBUTEREF()) {
            if (attributeRef == null || attributeRef.getNAME() == null || attributeRef.getNAME().isBlank()) {
                continue;
            }
            refs.add(attributeRef.getNAME());
        }
        return refs;
    }

    private static Map<String, List<String>> copy(Map<String, List<String>> source) {
        Map<String, List<String>> snapshot = new LinkedHashMap<>();
        if (source == null) {
            return snapshot;
        }
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            snapshot.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return snapshot;
    }
}
