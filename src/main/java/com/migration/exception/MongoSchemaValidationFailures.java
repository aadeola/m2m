package com.migration.exception;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoWriteException;
import com.mongodb.WriteError;
import com.mongodb.bulk.BulkWriteError;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.mongodb.BulkOperationException;

/**
 * Detects MongoDB document-validation failures (error code 121) in write exceptions.
 */
public final class MongoSchemaValidationFailures {

    private MongoSchemaValidationFailures() {
    }

    public static boolean isSchemaValidationFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof BulkOperationException bulkOperationException) {
                if (hasValidationError(bulkOperationException.getErrors())) {
                    return true;
                }
            }
            if (current instanceof MongoBulkWriteException mongoBulkWriteException) {
                if (hasValidationError(mongoBulkWriteException.getWriteErrors())) {
                    return true;
                }
            }
            if (current instanceof MongoWriteException mongoWriteException) {
                WriteError error = mongoWriteException.getError();
                if (error != null && error.getCode() == MongoSchemaValidationException.DOCUMENT_VALIDATION_FAILURE_CODE) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    public static String detailMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof BulkOperationException bulkOperationException) {
                String detail = formatErrors(bulkOperationException.getErrors());
                if (!detail.isEmpty()) {
                    return detail;
                }
            }
            if (current instanceof MongoBulkWriteException mongoBulkWriteException) {
                String detail = formatErrors(mongoBulkWriteException.getWriteErrors());
                if (!detail.isEmpty()) {
                    return detail;
                }
            }
            if (current instanceof MongoWriteException mongoWriteException) {
                WriteError error = mongoWriteException.getError();
                if (error != null
                        && error.getCode() == MongoSchemaValidationException.DOCUMENT_VALIDATION_FAILURE_CODE) {
                    return error.getMessage();
                }
            }
            current = current.getCause();
        }
        return throwable.getMessage() == null ? throwable.toString() : throwable.getMessage();
    }

    public static RuntimeException wrapIfSchemaValidation(Class<?> documentClass, RuntimeException cause) {
        if (!isSchemaValidationFailure(cause)) {
            return cause;
        }
        String detail = detailMessage(cause);
        return new MongoSchemaValidationException(
                "MongoDB schema validation failed writing " + documentClass.getSimpleName()
                        + " (validationAction=error, code=121): " + detail,
                cause);
    }

    private static boolean hasValidationError(List<? extends WriteError> errors) {
        if (errors == null || errors.isEmpty()) {
            return false;
        }
        return errors.stream()
                .anyMatch(error -> error.getCode() == MongoSchemaValidationException.DOCUMENT_VALIDATION_FAILURE_CODE);
    }

    private static String formatErrors(List<? extends WriteError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "";
        }
        return errors.stream()
                .filter(error -> error.getCode() == MongoSchemaValidationException.DOCUMENT_VALIDATION_FAILURE_CODE)
                .map(error -> {
                    if (error instanceof BulkWriteError bulkWriteError) {
                        return "index=" + bulkWriteError.getIndex() + " " + bulkWriteError.getMessage();
                    }
                    return error.getMessage();
                })
                .collect(Collectors.joining("; "));
    }
}
