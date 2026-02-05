package fr.expand.project.importdata.dto;

import fr.expand.project.importdata.dto.generated.OBJECT;

/**
 * Alias for OBJECT class for backward compatibility
 */
public class DataPackObject extends OBJECT {
    private Integer internalId;

    public DataPackObject() {
        super();
    }

    /**
     * Internal Neo4j id returned after insertion.
     */
    public Integer getInternalId() {
        return internalId;
    }

    public void setInternalId(Integer internalId) {
        this.internalId = internalId;
    }
}
