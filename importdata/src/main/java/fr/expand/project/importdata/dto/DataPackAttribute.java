package fr.expand.project.importdata.dto;

import fr.expand.project.importdata.dto.generated.ATTRIBUTE;

/**
 * Alias for ATTRIBUTE class for backward compatibility
 */
public class DataPackAttribute extends ATTRIBUTE {
    public DataPackAttribute() {
        super();
    }
    
    public DataPackAttribute(String key, String value) {
        super();
        setKEY(key);
        setVALUE(value);
    }
}
