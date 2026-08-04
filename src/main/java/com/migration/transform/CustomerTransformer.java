package com.migration.transform;

import com.migration.dto.CustomerResponse;
import com.migration.model.jpa.CustomerEntity;
import com.migration.model.mongo.CustomerDocument;
import com.migration.routing.DataSourceResolver;
import org.springframework.stereotype.Component;

@Component
public class CustomerTransformer {

    public CustomerDocument toDocument(CustomerEntity entity) {
        CustomerDocument document = new CustomerDocument();
        document.setId(String.valueOf(entity.getCustomerId()));
        document.setFirstName(entity.getFirstName());
        document.setLastName(entity.getLastName());
        document.setAccountNumber(entity.getAccountNumber());
        document.setPhoneNumber(entity.getPhoneNumber());
        document.setEmail(entity.getEmail());
        document.setCreatedAt(entity.getCreatedAt());
        return document;
    }

    public CustomerResponse toResponse(CustomerEntity entity) {
        CustomerResponse response = new CustomerResponse();
        response.setCustomerId(entity.getCustomerId());
        response.setFirstName(entity.getFirstName());
        response.setLastName(entity.getLastName());
        response.setAccountNumber(entity.getAccountNumber());
        response.setPhoneNumber(entity.getPhoneNumber());
        response.setEmail(entity.getEmail());
        response.setCreatedAt(entity.getCreatedAt());
        return response;
    }

    public CustomerResponse toResponse(CustomerDocument document) {
        CustomerResponse response = new CustomerResponse();
        response.setCustomerId(parseId(document.getId()));
        response.setFirstName(document.getFirstName());
        response.setLastName(document.getLastName());
        response.setAccountNumber(document.getAccountNumber());
        response.setPhoneNumber(document.getPhoneNumber());
        response.setEmail(document.getEmail());
        response.setCreatedAt(document.getCreatedAt());
        return response;
    }

    private Object parseId(String id) {
        if (DataSourceResolver.isNumericId(id)) {
            return Integer.parseInt(id);
        }
        return id;
    }
}
