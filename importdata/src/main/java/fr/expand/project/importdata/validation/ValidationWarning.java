package fr.expand.project.importdata.validation;

/**
 * Validation warning
 */
public class ValidationWarning {
    
    private String context;
    private String message;
    
    public ValidationWarning(String context, String message) {
        this.context = context;
        this.message = message;
    }
    
    public String getContext() {
        return context;
    }
    
    public String getMessage() {
        return message;
    }
    
    @Override
    public String toString() {
        return "[" + context + "] " + message;
    }
}
