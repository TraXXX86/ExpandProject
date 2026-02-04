package fr.expand.project.importdata.validation;

import java.util.List;

/**
 * Result of data validation
 */
public class ValidationResult {
    
    private List<ValidationError> errors;
    private List<ValidationWarning> warnings;
    
    public ValidationResult(List<ValidationError> errors, List<ValidationWarning> warnings) {
        this.errors = errors;
        this.warnings = warnings;
    }
    
    /**
     * Check if validation passed (no errors)
     * @return true if no errors, false otherwise
     */
    public boolean isValid() {
        return errors.isEmpty();
    }
    
    /**
     * Get all errors
     * @return List of errors
     */
    public List<ValidationError> getErrors() {
        return errors;
    }
    
    /**
     * Get all warnings
     * @return List of warnings
     */
    public List<ValidationWarning> getWarnings() {
        return warnings;
    }
    
    /**
     * Get formatted report
     * @return String report of validation results
     */
    public String getReport() {
        StringBuilder report = new StringBuilder();
        report.append("Validation Result:\n");
        report.append("==================\n");
        report.append("Status: ").append(isValid() ? "VALID" : "INVALID").append("\n");
        report.append("Errors: ").append(errors.size()).append("\n");
        report.append("Warnings: ").append(warnings.size()).append("\n\n");
        
        if (!errors.isEmpty()) {
            report.append("ERRORS:\n");
            report.append("-------\n");
            for (ValidationError error : errors) {
                report.append("  [").append(error.getContext()).append("] ")
                      .append(error.getMessage()).append("\n");
            }
            report.append("\n");
        }
        
        if (!warnings.isEmpty()) {
            report.append("WARNINGS:\n");
            report.append("---------\n");
            for (ValidationWarning warning : warnings) {
                report.append("  [").append(warning.getContext()).append("] ")
                      .append(warning.getMessage()).append("\n");
            }
        }
        
        return report.toString();
    }
    
    @Override
    public String toString() {
        return getReport();
    }
}
