package com.migration.transform;

import com.migration.dto.ProductResponse;
import com.migration.model.jpa.ProductEntity;
import com.migration.model.mongo.ProductDocument;
import com.migration.routing.DataSourceResolver;
import org.springframework.stereotype.Component;

@Component
public class ProductTransformer {

    public ProductDocument toDocument(ProductEntity entity) {
        ProductDocument document = new ProductDocument();
        document.setId(String.valueOf(entity.getProductId()));
        document.setName(entity.getName());
        document.setSku(entity.getSku());
        document.setPrice(entity.getPrice());
        return document;
    }

    public ProductResponse toResponse(ProductEntity entity) {
        ProductResponse response = new ProductResponse();
        response.setProductId(entity.getProductId());
        response.setName(entity.getName());
        response.setSku(entity.getSku());
        response.setPrice(entity.getPrice());
        return response;
    }

    public ProductResponse toResponse(ProductDocument document) {
        ProductResponse response = new ProductResponse();
        response.setProductId(parseId(document.getId()));
        response.setName(document.getName());
        response.setSku(document.getSku());
        response.setPrice(document.getPrice());
        return response;
    }

    private Object parseId(String id) {
        if (DataSourceResolver.isNumericId(id)) {
            return Integer.parseInt(id);
        }
        return id;
    }
}
