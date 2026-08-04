package com.migration.exception;

/**
 * Raised when a MongoDB write is rejected by collection schema validation
 * ({@code validationAction=error}, server error code 121).
 */
public class MongoSchemaValidationException extends RuntimeException {

    public static final int DOCUMENT_VALIDATION_FAILURE_CODE = 121;

    public MongoSchemaValidationException(String message) {
        super(message);
    }

    public MongoSchemaValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
