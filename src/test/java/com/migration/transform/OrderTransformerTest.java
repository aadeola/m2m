package com.migration.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.migration.dto.CreateOrderRequest;
import com.migration.dto.LineItemRequest;
import com.migration.dto.OrderResponse;
import com.migration.dto.OrderStatusResponse;
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

    private static final String OBJECT_ID = "507f191e810c19729de860ec";

    private final OrderTransformer transformer = new OrderTransformer();

    @Test
    void toDocument_fromPostgresEmbedsCustomerAndLineItems() {
        OrderEntity order = orderEntity(2, 7);
        LineItemEntity lineItem = lineItem(10, 2, 3, 2, "19.99");
        CustomerEntity customer = customer(7);
        ProductEntity product = product(3, "Hub", "49.99");

        OrderDocument document = transformer.toDocument(order, List.of(lineItem), customer, Map.of(3, product));

        assertEquals("2", document.getId());
        assertEquals(7, document.getCustomerId());
        assertNotNull(document.getCustomer());
        assertEquals("7", document.getCustomer().getCustomerId());
        assertEquals(1, document.getLineItems().size());
        EmbeddedLineItem embedded = document.getLineItems().getFirst();
        assertEquals(10, embedded.getLineItemId());
        assertEquals(3, embedded.getProduct().getProductId());
    }

    @Test
    void toResponse_fromPostgresEntity_matchesLegacyShape() {
        OrderEntity order = orderEntity(2, 7);
        LineItemEntity lineItem = lineItem(10, 2, 3, 2, "19.99");

        OrderResponse response = transformer.toResponse(order, List.of(lineItem), true);

        assertEquals(2, response.getOrderId());
        assertEquals(7, response.getCustomerId());
        assertEquals(1, response.getLineItems().size());
        assertEquals(3, response.getLineItems().getFirst().getProductId());
    }

    @Test
    void toResponse_fromMongoDocument_flattensEmbeddedLineItems() {
        OrderDocument document = new OrderDocument();
        document.setId("2");
        document.setCustomerId(7);
        document.setOrderDate(LocalDate.of(2025, 3, 1));
        document.setStatus("SHIPPED");
        document.setTotalAmount(new BigDecimal("39.98"));

        EmbeddedLineItem embedded = new EmbeddedLineItem();
        embedded.setLineItemId(10);
        embedded.setQuantity(2);
        embedded.setUnitPrice(new BigDecimal("19.99"));
        com.migration.model.mongo.EmbeddedProduct embeddedProduct = new com.migration.model.mongo.EmbeddedProduct();
        embeddedProduct.setProductId(3);
        embedded.setProduct(embeddedProduct);
        document.setLineItems(List.of(embedded));

        OrderResponse response = transformer.toResponse(document, true);

        assertEquals(2, response.getOrderId());
        assertEquals(3, response.getLineItems().getFirst().getProductId());
    }

    @Test
    void toResponse_fromMongoDocument_objectIdRemainsString() {
        OrderDocument document = new OrderDocument();
        document.setId(OBJECT_ID);
        document.setCustomerId(7);
        document.setOrderDate(LocalDate.of(2025, 3, 1));
        document.setStatus("PENDING");
        document.setTotalAmount(new BigDecimal("10.00"));

        OrderResponse response = transformer.toResponse(document, false);

        assertEquals(OBJECT_ID, response.getOrderId());
    }

    @Test
    void toDocument_fromCreateRequest_assignsObjectIdAndComputesTotal() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(1);
        LineItemRequest lineItemRequest = new LineItemRequest();
        lineItemRequest.setProductId(3);
        lineItemRequest.setQuantity(2);
        request.setLineItems(List.of(lineItemRequest));

        CustomerEntity customer = customer(1);
        ProductEntity product = product(3, "Hub", "49.99");

        OrderDocument document = transformer.toDocument(OBJECT_ID, request, customer, Map.of(3, product));

        assertEquals(OBJECT_ID, document.getId());
        assertEquals("PENDING", document.getStatus());
        assertEquals(new BigDecimal("99.98"), document.getTotalAmount());
        assertEquals(1, document.getLineItems().size());
    }

    @Test
    void toStatusResponse_fromMongoDocument_preservesLegacyFields() {
        OrderDocument document = new OrderDocument();
        document.setId("2");
        document.setStatus("DELIVERED");

        OrderStatusResponse response = transformer.toStatusResponse(document);

        assertEquals(2, response.getOrderId());
        assertEquals("DELIVERED", response.getStatus());
    }

    private static OrderEntity orderEntity(int orderId, int customerId) {
        OrderEntity order = new OrderEntity();
        order.setOrderId(orderId);
        order.setCustomerId(customerId);
        order.setOrderDate(LocalDate.of(2025, 3, 1));
        order.setStatus("SHIPPED");
        order.setTotalAmount(new BigDecimal("39.98"));
        return order;
    }

    private static LineItemEntity lineItem(int lineItemId, int orderId, int productId, int qty, String price) {
        LineItemEntity lineItem = new LineItemEntity();
        lineItem.setLineItemId(lineItemId);
        lineItem.setOrderId(orderId);
        lineItem.setProductId(productId);
        lineItem.setQuantity(qty);
        lineItem.setUnitPrice(new BigDecimal(price));
        return lineItem;
    }

    private static CustomerEntity customer(int customerId) {
        CustomerEntity customer = new CustomerEntity();
        customer.setCustomerId(customerId);
        customer.setFirstName("Alice");
        customer.setLastName("Smith");
        customer.setEmail("alice@example.com");
        return customer;
    }

    private static ProductEntity product(int productId, String name, String price) {
        ProductEntity product = new ProductEntity();
        product.setProductId(productId);
        product.setName(name);
        product.setSku("SKU-" + productId);
        product.setPrice(new BigDecimal(price));
        return product;
    }
}
