package fr.expand.project.importdata.dto;

import fr.expand.project.importdata.dto.generated.OBJLINK;

/**
 * Alias for OBJLINK class for backward compatibility
 */
public class DataPackObjectLink extends OBJLINK {
    public DataPackObjectLink() {
        super();
    }
    
    public DataPackObjectLink(int id, String type) {
        super();
        setID(id);
        setTYPE(type);
    }
}
