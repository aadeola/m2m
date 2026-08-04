package com.migration.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.bulk.BulkWriteResult;
import java.util.List;
import java.util.Set;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.BulkOperationException;

class MongoSchemaValidationFailuresTest {

    @Test
    void wrapIfSchemaValidation_wrapsCode121FromMongoWriteException() {
        WriteError writeError = new WriteError(
                MongoSchemaValidationException.DOCUMENT_VALIDATION_FAILURE_CODE,
                "Document failed validation",
                new BsonDocument());
        MongoWriteException cause = new MongoWriteException(writeError, new ServerAddress("localhost", 27017));

        RuntimeException wrapped = MongoSchemaValidationFailures.wrapIfSchemaValidation(OrderDoc.class, cause);

        assertInstanceOf(MongoSchemaValidationException.class, wrapped);
        assertTrue(wrapped.getMessage().contains("OrderDoc"));
        assertTrue(wrapped.getMessage().contains("code=121"));
        assertTrue(wrapped.getMessage().contains("Document failed validation"));
        assertSame(cause, wrapped.getCause());
    }

    @Test
    void wrapIfSchemaValidation_wrapsCode121FromBulkOperationException() {
        BulkWriteError bulkWriteError = new BulkWriteError(
                MongoSchemaValidationException.DOCUMENT_VALIDATION_FAILURE_CODE,
                "missing required property lineItems",
                new BsonDocument(),
                2);
        MongoBulkWriteException mongoBulk = new MongoBulkWriteException(
                BulkWriteResult.unacknowledged(),
                List.of(bulkWriteError),
                null,
                new ServerAddress("localhost", 27017),
                Set.of());
        BulkOperationException cause = new BulkOperationException("Bulk write failed", mongoBulk);

        RuntimeException wrapped = MongoSchemaValidationFailures.wrapIfSchemaValidation(OrderDoc.class, cause);

        assertInstanceOf(MongoSchemaValidationException.class, wrapped);
        assertTrue(wrapped.getMessage().contains("index=2"));
        assertTrue(wrapped.getMessage().contains("missing required property lineItems"));
        assertSame(cause, wrapped.getCause());
    }

    @Test
    void wrapIfSchemaValidation_leavesNon121ErrorsUnchanged() {
        WriteError writeError = new WriteError(11000, "E11000 duplicate key", new BsonDocument());
        MongoWriteException cause = new MongoWriteException(writeError, new ServerAddress("localhost", 27017));

        RuntimeException result = MongoSchemaValidationFailures.wrapIfSchemaValidation(OrderDoc.class, cause);

        assertSame(cause, result);
        assertFalse(result instanceof MongoSchemaValidationException);
    }

    @Test
    void isSchemaValidationFailure_findsNestedCause() {
        WriteError writeError = new WriteError(
                MongoSchemaValidationException.DOCUMENT_VALIDATION_FAILURE_CODE,
                "invalid",
                new BsonDocument());
        MongoWriteException nested = new MongoWriteException(writeError, new ServerAddress("localhost", 27017));
        RuntimeException outer = new RuntimeException("wrapper", nested);

        assertTrue(MongoSchemaValidationFailures.isSchemaValidationFailure(outer));
        assertEquals("invalid", MongoSchemaValidationFailures.detailMessage(outer));
    }

    private static final class OrderDoc {
    }
}
