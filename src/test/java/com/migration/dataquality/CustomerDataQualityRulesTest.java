package com.migration.dataquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.migration.model.mongo.CustomerDocument;
import com.migration.quality.CustomerDataQualityRules;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomerDataQualityRulesTest {

    private final CustomerDataQualityRules rules = new CustomerDataQualityRules();

    @Test
    void detectInvalidFields_returnsEmptyForValidDocument() {
        CustomerDocument document = validDocument();

        assertTrue(rules.detectInvalidFields(document).isEmpty());
    }

    @Test
    void detectInvalidFields_flagsBadEmail() {
        CustomerDocument document = validDocument();
        document.setEmail("not-an-email");

        assertEquals(List.of("email"), rules.detectInvalidFields(document));
    }

    @Test
    void detectInvalidFields_flagsMultipleFields() {
        CustomerDocument document = validDocument();
        document.setEmail("bad.example.com");
        document.setPhoneNumber("123");

        List<String> invalidFields = rules.detectInvalidFields(document);
        assertEquals(2, invalidFields.size());
        assertTrue(invalidFields.contains("email"));
        assertTrue(invalidFields.contains("phone_number"));
    }

    @Test
    void detectInvalidFields_flagsShortFirstName() {
        CustomerDocument document = validDocument();
        document.setFirstName("A");

        assertEquals(List.of("first_name"), rules.detectInvalidFields(document));
    }

    @Test
    void detectInvalidFields_flagsLastNameWithDigit() {
        CustomerDocument document = validDocument();
        document.setLastName("Smith1");

        assertEquals(List.of("last_name"), rules.detectInvalidFields(document));
    }

    @Test
    void detectInvalidFields_flagsBadAccountNumber() {
        CustomerDocument document = validDocument();
        document.setAccountNumber("AB12345");

        assertEquals(List.of("account_number"), rules.detectInvalidFields(document));
    }

    private CustomerDocument validDocument() {
        CustomerDocument document = new CustomerDocument();
        document.setId("1");
        document.setFirstName("Alice");
        document.setLastName("Johnson");
        document.setAccountNumber("CUS0001");
        document.setPhoneNumber("5550000001");
        document.setEmail("alice@example.com");
        return document;
    }
}
