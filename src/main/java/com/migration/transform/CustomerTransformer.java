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
        document.setName(entity.getName());
        document.setEmail(entity.getEmail());
        document.setCreatedAt(entity.getCreatedAt());
        return document;
    }

    public CustomerResponse toResponse(CustomerEntity entity) {
        CustomerResponse response = new CustomerResponse();
        response.setCustomerId(entity.getCustomerId());
        response.setName(entity.getName());
        response.setEmail(entity.getEmail());
        response.setCreatedAt(entity.getCreatedAt());
        return response;
    }

    public CustomerResponse toResponse(CustomerDocument document) {
        CustomerResponse response = new CustomerResponse();
        response.setCustomerId(parseId(document.getId()));
        response.setName(document.getName());
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
