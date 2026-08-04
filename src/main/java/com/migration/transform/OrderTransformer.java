package com.migration.transform;

import com.migration.dto.CreateOrderRequest;
import com.migration.dto.LineItemRequest;
import com.migration.dto.LineItemResponse;
import com.migration.dto.OrderResponse;
import com.migration.dto.OrderStatusResponse;
import com.migration.model.jpa.CustomerEntity;
import com.migration.model.jpa.LineItemEntity;
import com.migration.model.jpa.OrderEntity;
import com.migration.model.jpa.ProductEntity;
import com.migration.model.mongo.EmbeddedCustomerSummary;
import com.migration.model.mongo.EmbeddedLineItem;
import com.migration.model.mongo.EmbeddedProduct;
import com.migration.model.mongo.OrderDocument;
import com.migration.routing.DataSourceResolver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class OrderTransformer {

    public OrderDocument toDocument(
            OrderEntity order,
            List<LineItemEntity> lineItems,
            CustomerEntity customer,
            Map<Integer, ProductEntity> productsById) {
        OrderDocument document = new OrderDocument();
        document.setId(String.valueOf(order.getOrderId()));
        document.setCustomerId(order.getCustomerId());
        document.setOrderDate(order.getOrderDate());
        document.setStatus(order.getStatus());
        document.setTotalAmount(order.getTotalAmount());
        document.setCustomer(toCustomerSummary(customer));
        document.setLineItems(toEmbeddedLineItems(lineItems, productsById));
        return document;
    }

    public OrderDocument toDocument(
            String objectId,
            CreateOrderRequest request,
            CustomerEntity customer,
            Map<Integer, ProductEntity> productsById) {
        OrderDocument document = new OrderDocument();
        document.setId(objectId);
        document.setCustomerId(request.getCustomerId());
        document.setOrderDate(LocalDate.now());
        document.setStatus("PENDING");

        List<EmbeddedLineItem> embeddedLineItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int syntheticLineItemId = 1;
        for (LineItemRequest item : request.getLineItems()) {
            ProductEntity product = productsById.get(item.getProductId());
            BigDecimal unitPrice = product.getPrice();
            EmbeddedLineItem embedded = new EmbeddedLineItem();
            embedded.setLineItemId(syntheticLineItemId++);
            embedded.setQuantity(item.getQuantity());
            embedded.setUnitPrice(unitPrice);
            embedded.setProduct(toEmbeddedProduct(product));
            embeddedLineItems.add(embedded);
            total = total.add(unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        document.setTotalAmount(total);
        document.setCustomer(toCustomerSummary(customer));
        document.setLineItems(embeddedLineItems);
        return document;
    }

    public OrderResponse toResponse(OrderEntity order, List<LineItemEntity> lineItems, boolean includeLineItems) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getOrderId());
        response.setCustomerId(order.getCustomerId());
        response.setOrderDate(order.getOrderDate());
        response.setStatus(order.getStatus());
        response.setTotalAmount(order.getTotalAmount());
        if (includeLineItems) {
            response.setLineItems(lineItems.stream().map(this::toLineItemResponse).collect(Collectors.toList()));
        }
        return response;
    }

    public OrderResponse toResponse(OrderDocument document, boolean includeLineItems) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(parseId(document.getId()));
        response.setCustomerId(document.getCustomerId());
        response.setOrderDate(document.getOrderDate());
        response.setStatus(document.getStatus());
        response.setTotalAmount(document.getTotalAmount());
        if (includeLineItems && document.getLineItems() != null) {
            response.setLineItems(document.getLineItems().stream()
                    .map(this::toLineItemResponse)
                    .collect(Collectors.toList()));
        }
        return response;
    }

    public OrderStatusResponse toStatusResponse(OrderEntity order) {
        OrderStatusResponse response = new OrderStatusResponse();
        response.setOrderId(order.getOrderId());
        response.setStatus(order.getStatus());
        return response;
    }

    public OrderStatusResponse toStatusResponse(OrderDocument document) {
        OrderStatusResponse response = new OrderStatusResponse();
        response.setOrderId(parseId(document.getId()));
        response.setStatus(document.getStatus());
        return response;
    }

    private EmbeddedCustomerSummary toCustomerSummary(CustomerEntity customer) {
        EmbeddedCustomerSummary summary = new EmbeddedCustomerSummary();
        summary.setCustomerId(String.valueOf(customer.getCustomerId()));
        summary.setFirstName(customer.getFirstName());
        summary.setLastName(customer.getLastName());
        summary.setAccountNumber(customer.getAccountNumber());
        summary.setPhoneNumber(customer.getPhoneNumber());
        summary.setEmail(customer.getEmail());
        return summary;
    }

    private List<EmbeddedLineItem> toEmbeddedLineItems(
            List<LineItemEntity> lineItems,
            Map<Integer, ProductEntity> productsById) {
        return lineItems.stream().map(item -> {
            EmbeddedLineItem embedded = new EmbeddedLineItem();
            embedded.setLineItemId(item.getLineItemId());
            embedded.setQuantity(item.getQuantity());
            embedded.setUnitPrice(item.getUnitPrice());
            ProductEntity product = productsById.get(item.getProductId());
            if (product == null) {
                throw new IllegalStateException(
                        "Missing embedded product " + item.getProductId() + " for line item " + item.getLineItemId());
            }
            embedded.setProduct(toEmbeddedProduct(product));
            return embedded;
        }).collect(Collectors.toList());
    }

    private EmbeddedProduct toEmbeddedProduct(ProductEntity product) {
        EmbeddedProduct embedded = new EmbeddedProduct();
        embedded.setProductId(product.getProductId());
        embedded.setName(product.getName());
        embedded.setPrice(product.getPrice());
        return embedded;
    }

    private LineItemResponse toLineItemResponse(LineItemEntity entity) {
        LineItemResponse response = new LineItemResponse();
        response.setLineItemId(entity.getLineItemId());
        response.setProductId(entity.getProductId());
        response.setQuantity(entity.getQuantity());
        response.setUnitPrice(entity.getUnitPrice());
        return response;
    }

    private LineItemResponse toLineItemResponse(EmbeddedLineItem item) {
        LineItemResponse response = new LineItemResponse();
        response.setLineItemId(item.getLineItemId());
        response.setQuantity(item.getQuantity());
        response.setUnitPrice(item.getUnitPrice());
        if (item.getProduct() != null) {
            response.setProductId(item.getProduct().getProductId());
        }
        return response;
    }

    private Object parseId(String id) {
        if (DataSourceResolver.isNumericId(id)) {
            return Integer.parseInt(id);
        }
        return id;
    }
}
