package fr.expand.project.importdata.validation;

/**
 * Validation error
 */
public class ValidationError {
    
    private String context;
    private String message;
    
    public ValidationError(String context, String message) {
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
