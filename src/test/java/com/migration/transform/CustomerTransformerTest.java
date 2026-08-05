package com.migration.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.migration.dto.CustomerResponse;
import com.migration.model.jpa.CustomerEntity;
import com.migration.model.mongo.CustomerDocument;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CustomerTransformerTest {

    private final CustomerTransformer transformer = new CustomerTransformer();

    @Test
    void toDocument_mapsEntityFieldsAndUsesStringPkAsMongoId() {
        CustomerEntity entity = new CustomerEntity();
        entity.setCustomerId(7);
        entity.setFirstName("Alice");
        entity.setLastName("Smith");
        entity.setAccountNumber("CUS0007");
        entity.setPhoneNumber("5550000007");
        entity.setEmail("alice@example.com");
        entity.setCreatedAt(LocalDateTime.of(2025, 1, 1, 10, 0));

        CustomerDocument document = transformer.toDocument(entity);

        assertEquals("7", document.getId());
        assertEquals("Alice", document.getFirstName());
        assertEquals("Smith", document.getLastName());
        assertEquals("CUS0007", document.getAccountNumber());
        assertEquals("5550000007", document.getPhoneNumber());
        assertEquals("alice@example.com", document.getEmail());
        assertEquals(entity.getCreatedAt(), document.getCreatedAt());
    }

    @Test
    void toResponse_fromEntity_preservesLegacyShape() {
        CustomerEntity entity = new CustomerEntity();
        entity.setCustomerId(7);
        entity.setFirstName("Alice");
        entity.setLastName("Smith");
        entity.setAccountNumber("CUS0007");
        entity.setPhoneNumber("5550000007");
        entity.setEmail("alice@example.com");
        entity.setCreatedAt(LocalDateTime.of(2025, 1, 1, 10, 0));

        CustomerResponse response = transformer.toResponse(entity);

        assertEquals(7, response.getCustomerId());
        assertEquals("Alice", response.getFirstName());
        assertEquals("Smith", response.getLastName());
        assertEquals("CUS0007", response.getAccountNumber());
        assertEquals("5550000007", response.getPhoneNumber());
        assertEquals("alice@example.com", response.getEmail());
        assertEquals(entity.getCreatedAt(), response.getCreatedAt());
    }

    @Test
    void toResponse_fromDocument_numericIdParsesToInteger() {
        CustomerDocument document = new CustomerDocument();
        document.setId("7");
        document.setFirstName("Alice");
        document.setLastName("Smith");
        document.setAccountNumber("CUS0007");
        document.setPhoneNumber("5550000007");
        document.setEmail("alice@example.com");
        document.setCreatedAt(LocalDateTime.of(2025, 1, 1, 10, 0));

        CustomerResponse response = transformer.toResponse(document);

        assertEquals(7, response.getCustomerId());
        assertEquals("Alice", response.getFirstName());
        assertEquals("Smith", response.getLastName());
    }

    @Test
    void toResponse_fromDocument_objectIdRemainsString() {
        String objectId = "507f191e810c19729de860eb";
        CustomerDocument document = new CustomerDocument();
        document.setId(objectId);
        document.setFirstName("Bob");
        document.setLastName("Jones");
        document.setAccountNumber("CUS0008");
        document.setPhoneNumber("5550000008");
        document.setEmail("bob@example.com");

        CustomerResponse response = transformer.toResponse(document);

        assertEquals(objectId, response.getCustomerId());
        assertEquals("Bob", response.getFirstName());
        assertEquals("Jones", response.getLastName());
    }
}
