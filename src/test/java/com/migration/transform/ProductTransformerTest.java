package com.migration.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.migration.dto.ProductResponse;
import com.migration.model.jpa.ProductEntity;
import com.migration.model.mongo.ProductDocument;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProductTransformerTest {

    private final ProductTransformer transformer = new ProductTransformer();

    @Test
    void toDocument_mapsEntityFieldsAndUsesStringPkAsMongoId() {
        ProductEntity entity = new ProductEntity();
        entity.setProductId(3);
        entity.setName("Keyboard");
        entity.setSku("MK-002");
        entity.setPrice(new BigDecimal("89.99"));

        ProductDocument document = transformer.toDocument(entity);

        assertEquals("3", document.getId());
        assertEquals("Keyboard", document.getName());
        assertEquals("MK-002", document.getSku());
        assertEquals(new BigDecimal("89.99"), document.getPrice());
    }

    @Test
    void toResponse_fromEntity_preservesLegacyShape() {
        ProductEntity entity = new ProductEntity();
        entity.setProductId(3);
        entity.setName("Keyboard");
        entity.setSku("MK-002");
        entity.setPrice(new BigDecimal("89.99"));

        ProductResponse response = transformer.toResponse(entity);

        assertEquals(3, response.getProductId());
        assertEquals("Keyboard", response.getName());
        assertEquals("MK-002", response.getSku());
        assertEquals(new BigDecimal("89.99"), response.getPrice());
    }

    @Test
    void toResponse_fromDocument_numericIdParsesToInteger() {
        ProductDocument document = new ProductDocument();
        document.setId("3");
        document.setName("Keyboard");
        document.setSku("MK-002");
        document.setPrice(new BigDecimal("89.99"));

        ProductResponse response = transformer.toResponse(document);

        assertEquals(3, response.getProductId());
    }

    @Test
    void toResponse_fromDocument_objectIdRemainsString() {
        String objectId = "507f191e810c19729de860ea";
        ProductDocument document = new ProductDocument();
        document.setId(objectId);
        document.setName("Mouse");
        document.setSku("WM-001");
        document.setPrice(new BigDecimal("29.99"));

        ProductResponse response = transformer.toResponse(document);

        assertEquals(objectId, response.getProductId());
    }
}
