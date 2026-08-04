package com.migration.quality;

import com.migration.model.mongo.CustomerDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Mirrors the customers collection {@code $jsonSchema} in seeds/mongo-init.js.
 */
@Component
public class CustomerDataQualityRules {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z]+([ -][A-Za-z]+)*$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("^[A-Za-z]{3}[0-9]{4}$");
    private static final Pattern PHONE_NUMBER_PATTERN = Pattern.compile("^[0-9]{10}$");

    public List<String> detectInvalidFields(CustomerDocument document) {
        List<String> invalidFields = new ArrayList<>();
        if (!isValidName(document.getFirstName())) {
            invalidFields.add("first_name");
        }
        if (!isValidName(document.getLastName())) {
            invalidFields.add("last_name");
        }
        if (!isValidEmail(document.getEmail())) {
            invalidFields.add("email");
        }
        if (!isValidAccountNumber(document.getAccountNumber())) {
            invalidFields.add("account_number");
        }
        if (document.getPhoneNumber() != null
                && !document.getPhoneNumber().isBlank()
                && !PHONE_NUMBER_PATTERN.matcher(document.getPhoneNumber()).matches()) {
            invalidFields.add("phone_number");
        }
        return invalidFields;
    }

    private boolean isValidName(String value) {
        return value != null && value.length() >= 2 && NAME_PATTERN.matcher(value).matches();
    }

    private boolean isValidEmail(String value) {
        return value != null && EMAIL_PATTERN.matcher(value).matches();
    }

    private boolean isValidAccountNumber(String value) {
        return value != null && ACCOUNT_NUMBER_PATTERN.matcher(value).matches();
    }
}
