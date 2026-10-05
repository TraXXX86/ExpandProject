package fr.expand.project.importdata.dao;

/** A persisted identity already exists or collides with another entity. */
public class StorageConflictException extends IllegalStateException {
    public StorageConflictException(String message) {
        super(message);
    }

    public StorageConflictException(String message, Throwable cause) {
        super(message, cause);
    }

    public static RuntimeException translate(org.neo4j.driver.exceptions.ClientException error) {
        if (error.code().contains("ConstraintValidationFailed"))
            return new StorageConflictException("A persisted identity already exists", error);
        return error;
    }
}
