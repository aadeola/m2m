package com.migration.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.migration.model.jpa.CustomerEntity;
import com.migration.model.jpa.LineItemEntity;
import com.migration.model.jpa.OrderEntity;
import com.migration.model.jpa.ProductEntity;
import com.migration.model.mongo.EmbeddedLineItem;
import com.migration.model.mongo.OrderDocument;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrderTransformerTest {

    @Test
    void toDocument_embedsProductForEveryLineItemWhenProductsAreAvailable() {
        OrderTransformer transformer = new OrderTransformer();

        OrderEntity order = new OrderEntity();
        order.setOrderId(1);
        order.setCustomerId(1);
        order.setOrderDate(LocalDate.of(2025, 1, 10));
        order.setStatus("SHIPPED");
        order.setTotalAmount(new BigDecimal("119.98"));

        LineItemEntity lineItem = new LineItemEntity();
        lineItem.setLineItemId(1);
        lineItem.setOrderId(1);
        lineItem.setProductId(6);
        lineItem.setQuantity(1);
        lineItem.setUnitPrice(new BigDecimal("24.99"));

        CustomerEntity customer = new CustomerEntity();
        customer.setCustomerId(1);
        customer.setFirstName("Alice");
        customer.setLastName("Johnson");

        ProductEntity product = new ProductEntity();
        product.setProductId(6);
        product.setName("Laptop Sleeve");
        product.setPrice(new BigDecimal("24.99"));

        OrderDocument document = transformer.toDocument(
                order,
                List.of(lineItem),
                customer,
                Map.of(6, product));

        assertEquals(1, document.getLineItems().size());
        EmbeddedLineItem embedded = document.getLineItems().get(0);
        assertNotNull(embedded.getProduct());
        assertEquals(6, embedded.getProduct().getProductId());
    }

    @Test
    void toDocument_failsFastWhenLineItemProductIsMissing() {
        OrderTransformer transformer = new OrderTransformer();

        OrderEntity order = new OrderEntity();
        order.setOrderId(1);
        order.setCustomerId(1);
        order.setOrderDate(LocalDate.of(2025, 1, 10));
        order.setStatus("SHIPPED");
        order.setTotalAmount(new BigDecimal("24.99"));

        LineItemEntity lineItem = new LineItemEntity();
        lineItem.setLineItemId(1);
        lineItem.setOrderId(1);
        lineItem.setProductId(6);
        lineItem.setQuantity(1);
        lineItem.setUnitPrice(new BigDecimal("24.99"));

        CustomerEntity customer = new CustomerEntity();
        customer.setCustomerId(1);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> transformer.toDocument(
                order,
                List.of(lineItem),
                customer,
                Map.of()));

        assertEquals("Missing embedded product 6 for line item 1", ex.getMessage());
    }
}
