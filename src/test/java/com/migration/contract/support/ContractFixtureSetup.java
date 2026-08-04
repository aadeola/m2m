package com.migration.contract.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.dto.CreateOrderRequest;
import com.migration.dto.CustomerResponse;
import com.migration.dto.LineItemRequest;
import com.migration.dto.LineItemResponse;
import com.migration.dto.OrderResponse;
import com.migration.dto.OrderStatusResponse;
import com.migration.dto.ProductResponse;
import com.migration.model.jpa.CustomerEntity;
import com.migration.model.jpa.LineItemEntity;
import com.migration.model.jpa.OrderEntity;
import com.migration.model.jpa.ProductEntity;
import com.migration.model.mongo.CustomerDocument;
import com.migration.model.mongo.EmbeddedCustomerSummary;
import com.migration.model.mongo.EmbeddedLineItem;
import com.migration.model.mongo.EmbeddedProduct;
import com.migration.model.mongo.OrderDocument;
import com.migration.model.mongo.ProductDocument;
import com.migration.repository.jpa.CustomerJpaRepository;
import com.migration.repository.jpa.LineItemJpaRepository;
import com.migration.repository.jpa.OrderJpaRepository;
import com.migration.repository.jpa.ProductJpaRepository;
import com.migration.repository.mongo.CustomerMongoRepository;
import com.migration.repository.mongo.OrderMongoRepository;
import com.migration.repository.mongo.ProductMongoRepository;
import com.migration.transform.CustomerTransformer;
import com.migration.transform.OrderTransformer;
import com.migration.transform.ProductTransformer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Seeds migrated/new (Mongo-native) fixtures and registers canonical legacy responses on the stub server.
 */
@Component
public class ContractFixtureSetup {

    private final CustomerJpaRepository customerJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final OrderJpaRepository orderJpaRepository;
    private final LineItemJpaRepository lineItemJpaRepository;
    private final CustomerMongoRepository customerMongoRepository;
    private final ProductMongoRepository productMongoRepository;
    private final OrderMongoRepository orderMongoRepository;
    private final CustomerTransformer customerTransformer;
    private final ProductTransformer productTransformer;
    private final OrderTransformer orderTransformer;
    private final ObjectMapper objectMapper;

    public ContractFixtureSetup(
            CustomerJpaRepository customerJpaRepository,
            ProductJpaRepository productJpaRepository,
            OrderJpaRepository orderJpaRepository,
            LineItemJpaRepository lineItemJpaRepository,
            CustomerMongoRepository customerMongoRepository,
            ProductMongoRepository productMongoRepository,
            OrderMongoRepository orderMongoRepository,
            CustomerTransformer customerTransformer,
            ProductTransformer productTransformer,
            OrderTransformer orderTransformer) {
        this.customerJpaRepository = customerJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.orderJpaRepository = orderJpaRepository;
        this.lineItemJpaRepository = lineItemJpaRepository;
        this.customerMongoRepository = customerMongoRepository;
        this.productMongoRepository = productMongoRepository;
        this.orderMongoRepository = orderMongoRepository;
        this.customerTransformer = customerTransformer;
        this.productTransformer = productTransformer;
        this.orderTransformer = orderTransformer;
        this.objectMapper = ContractAssertions.objectMapper();
    }

    public void prepareDatabaseAndLegacyStub(LegacyApiStub legacyApiStub) throws Exception {
        legacyApiStub.reset();
        customerMongoRepository.deleteAll();
        productMongoRepository.deleteAll();
        orderMongoRepository.deleteAll();
        resetMigrationFlags();

        migrateEntityFixtures();
        insertObjectIdFixtures();
        registerLegacyStubs(legacyApiStub);
    }

    private void resetMigrationFlags() {
        customerJpaRepository.findAll().forEach(entity -> entity.setMigratedAt(null));
        productJpaRepository.findAll().forEach(entity -> entity.setMigratedAt(null));
        orderJpaRepository.findAll().forEach(entity -> entity.setMigratedAt(null));
        customerJpaRepository.saveAll(customerJpaRepository.findAll());
        productJpaRepository.saveAll(productJpaRepository.findAll());
        orderJpaRepository.saveAll(orderJpaRepository.findAll());
    }

    private void migrateEntityFixtures() {
        migrateCustomer(ContractFixtures.MIGRATED_CUSTOMER_ID);
        migrateProduct(ContractFixtures.MIGRATED_PRODUCT_ID);
        migrateOrder(ContractFixtures.MIGRATED_ORDER_ID);
    }

    private void migrateCustomer(int customerId) {
        CustomerEntity entity = customerJpaRepository.findById(customerId).orElseThrow();
        customerMongoRepository.save(customerTransformer.toDocument(entity));
        entity.setMigratedAt(LocalDateTime.now());
        customerJpaRepository.save(entity);
    }

    private void migrateProduct(int productId) {
        ProductEntity entity = productJpaRepository.findById(productId).orElseThrow();
        productMongoRepository.save(productTransformer.toDocument(entity));
        entity.setMigratedAt(LocalDateTime.now());
        productJpaRepository.save(entity);
    }

    private void migrateOrder(int orderId) {
        OrderEntity order = orderJpaRepository.findById(orderId).orElseThrow();
        List<LineItemEntity> lineItems = lineItemJpaRepository.findByOrderIdOrderByLineItemIdAsc(orderId);
        CustomerEntity customer = customerJpaRepository.findById(order.getCustomerId()).orElseThrow();

        Map<Integer, ProductEntity> productsById = new HashMap<>();
        for (LineItemEntity lineItem : lineItems) {
            productsById.computeIfAbsent(
                    lineItem.getProductId(),
                    productId -> productJpaRepository.findById(productId).orElseThrow());
        }

        orderMongoRepository.save(orderTransformer.toDocument(order, lineItems, customer, productsById));
        order.setMigratedAt(LocalDateTime.now());
        orderJpaRepository.save(order);
    }

    private void insertObjectIdFixtures() {
        CustomerDocument objectIdCustomer = new CustomerDocument();
        objectIdCustomer.setId(ContractFixtures.OBJECT_ID_CUSTOMER);
        objectIdCustomer.setName("Frank Ocean");
        objectIdCustomer.setEmail("frank@example.com");
        objectIdCustomer.setCreatedAt(LocalDateTime.of(2026, 1, 15, 10, 30, 0));
        customerMongoRepository.save(objectIdCustomer);

        ProductDocument objectIdProduct = new ProductDocument();
        objectIdProduct.setId(ContractFixtures.OBJECT_ID_PRODUCT);
        objectIdProduct.setName("Cloud Sync License");
        objectIdProduct.setSku("CS-999");
        objectIdProduct.setPrice(new BigDecimal("199.99"));
        productMongoRepository.save(objectIdProduct);

        OrderDocument objectIdOrder = new OrderDocument();
        objectIdOrder.setId(ContractFixtures.OBJECT_ID_ORDER);
        objectIdOrder.setCustomerId(null);
        objectIdOrder.setOrderDate(LocalDate.of(2026, 2, 1));
        objectIdOrder.setStatus("PENDING");
        objectIdOrder.setTotalAmount(new BigDecimal("199.99"));

        EmbeddedCustomerSummary customerSummary = new EmbeddedCustomerSummary();
        customerSummary.setCustomerId(ContractFixtures.OBJECT_ID_CUSTOMER);
        customerSummary.setName(objectIdCustomer.getName());
        customerSummary.setEmail(objectIdCustomer.getEmail());
        objectIdOrder.setCustomer(customerSummary);

        EmbeddedLineItem lineItem = new EmbeddedLineItem();
        lineItem.setLineItemId(1);
        lineItem.setQuantity(1);
        lineItem.setUnitPrice(new BigDecimal("199.99"));
        EmbeddedProduct embeddedProduct = new EmbeddedProduct();
        embeddedProduct.setProductId(ContractFixtures.OBJECT_ID_PRODUCT);
        embeddedProduct.setName(objectIdProduct.getName());
        embeddedProduct.setPrice(objectIdProduct.getPrice());
        lineItem.setProduct(embeddedProduct);
        objectIdOrder.setLineItems(List.of(lineItem));

        orderMongoRepository.save(objectIdOrder);
    }

    private void registerLegacyStubs(LegacyApiStub legacyApiStub) throws Exception {
        for (RoutingScenario scenario : RoutingScenario.values()) {
            registerProductStub(legacyApiStub, scenario);
            registerCustomerStub(legacyApiStub, scenario);
            registerOrderStub(legacyApiStub, scenario);
            registerOrderStatusStub(legacyApiStub, scenario);
        }

        registerListStubs(legacyApiStub);
        registerCreateOrderStub(legacyApiStub);
    }

    private void registerProductStub(LegacyApiStub legacyApiStub, RoutingScenario scenario) throws Exception {
        String id = ContractFixtures.productId(scenario);
        ProductResponse response = buildProductResponse(scenario);
        legacyApiStub.registerGet("/products/" + id, objectMapper.writeValueAsString(response));
    }

    private void registerCustomerStub(LegacyApiStub legacyApiStub, RoutingScenario scenario) throws Exception {
        String id = ContractFixtures.customerId(scenario);
        CustomerResponse response = buildCustomerResponse(scenario);
        legacyApiStub.registerGet("/customers/" + id, objectMapper.writeValueAsString(response));
    }

    private void registerOrderStub(LegacyApiStub legacyApiStub, RoutingScenario scenario) throws Exception {
        String id = ContractFixtures.orderId(scenario);
        OrderResponse response = buildOrderResponse(scenario, true);
        legacyApiStub.registerGet("/orders/" + id, objectMapper.writeValueAsString(response));
    }

    private void registerOrderStatusStub(LegacyApiStub legacyApiStub, RoutingScenario scenario) throws Exception {
        String id = ContractFixtures.orderId(scenario);
        OrderStatusResponse response = buildOrderStatusResponse(scenario);
        legacyApiStub.registerGet("/orders/" + id + "/status", objectMapper.writeValueAsString(response));
    }

    private void registerListStubs(LegacyApiStub legacyApiStub) throws Exception {
        legacyApiStub.registerGet("/customers", objectMapper.writeValueAsString(buildAllCustomersResponse()));
        legacyApiStub.registerGet("/orders", objectMapper.writeValueAsString(buildAllOrdersResponse()));
        legacyApiStub.registerGetWithQuery(
                "/orders",
                "customer_id",
                String.valueOf(ContractFixtures.UNMIGRATED_CUSTOMER_ID),
                objectMapper.writeValueAsString(buildCustomerOrdersResponse(ContractFixtures.UNMIGRATED_CUSTOMER_ID)));
        legacyApiStub.registerGetWithQuery(
                "/orders",
                "customer_id",
                String.valueOf(ContractFixtures.MIGRATED_CUSTOMER_ID),
                objectMapper.writeValueAsString(buildCustomerOrdersResponse(ContractFixtures.MIGRATED_CUSTOMER_ID)));
        legacyApiStub.registerGet(
                "/customers/" + ContractFixtures.UNMIGRATED_CUSTOMER_ID + "/orders",
                objectMapper.writeValueAsString(buildCustomerOrdersResponse(ContractFixtures.UNMIGRATED_CUSTOMER_ID)));
        legacyApiStub.registerGet(
                "/customers/" + ContractFixtures.MIGRATED_CUSTOMER_ID + "/orders",
                objectMapper.writeValueAsString(buildCustomerOrdersResponse(ContractFixtures.MIGRATED_CUSTOMER_ID)));
        legacyApiStub.registerGet(
                "/customers/" + ContractFixtures.OBJECT_ID_CUSTOMER + "/orders",
                objectMapper.writeValueAsString(buildObjectIdCustomerOrdersResponse()));
    }

    private void registerCreateOrderStub(LegacyApiStub legacyApiStub) throws Exception {
        CreateOrderRequest request = sampleCreateOrderRequest();
        OrderResponse template = new OrderResponse();
        template.setCustomerId(request.getCustomerId());
        template.setStatus("PENDING");
        template.setTotalAmount(new BigDecimal("29.99"));
        LineItemResponse lineItem = new LineItemResponse();
        lineItem.setProductId(ContractFixtures.UNMIGRATED_PRODUCT_ID);
        lineItem.setQuantity(1);
        lineItem.setUnitPrice(new BigDecimal("29.99"));
        template.setLineItems(List.of(lineItem));
        legacyApiStub.registerPost("/orders", objectMapper.writeValueAsString(template));
    }

    public CreateOrderRequest sampleCreateOrderRequest() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(ContractFixtures.UNMIGRATED_CUSTOMER_ID);
        LineItemRequest lineItem = new LineItemRequest();
        lineItem.setProductId(ContractFixtures.UNMIGRATED_PRODUCT_ID);
        lineItem.setQuantity(1);
        request.setLineItems(List.of(lineItem));
        return request;
    }

    private ProductResponse buildProductResponse(RoutingScenario scenario) {
        return switch (scenario) {
            case UNMIGRATED -> productTransformer.toResponse(
                    productJpaRepository.findById(ContractFixtures.UNMIGRATED_PRODUCT_ID).orElseThrow());
            case MIGRATED -> productTransformer.toResponse(
                    productMongoRepository.findById(String.valueOf(ContractFixtures.MIGRATED_PRODUCT_ID)).orElseThrow());
            case NEW -> productTransformer.toResponse(
                    productMongoRepository.findById(ContractFixtures.OBJECT_ID_PRODUCT).orElseThrow());
        };
    }

    private CustomerResponse buildCustomerResponse(RoutingScenario scenario) {
        return switch (scenario) {
            case UNMIGRATED -> customerTransformer.toResponse(
                    customerJpaRepository.findById(ContractFixtures.UNMIGRATED_CUSTOMER_ID).orElseThrow());
            case MIGRATED -> customerTransformer.toResponse(
                    customerMongoRepository.findById(String.valueOf(ContractFixtures.MIGRATED_CUSTOMER_ID)).orElseThrow());
            case NEW -> customerTransformer.toResponse(
                    customerMongoRepository.findById(ContractFixtures.OBJECT_ID_CUSTOMER).orElseThrow());
        };
    }

    private OrderResponse buildOrderResponse(RoutingScenario scenario, boolean includeLineItems) {
        return switch (scenario) {
            case UNMIGRATED -> {
                OrderEntity order = orderJpaRepository.findById(ContractFixtures.UNMIGRATED_ORDER_ID).orElseThrow();
                List<LineItemEntity> lineItems = includeLineItems
                        ? lineItemJpaRepository.findByOrderIdOrderByLineItemIdAsc(order.getOrderId())
                        : List.of();
                yield orderTransformer.toResponse(order, lineItems, includeLineItems);
            }
            case MIGRATED -> orderTransformer.toResponse(
                    orderMongoRepository.findById(String.valueOf(ContractFixtures.MIGRATED_ORDER_ID)).orElseThrow(),
                    includeLineItems);
            case NEW -> orderTransformer.toResponse(
                    orderMongoRepository.findById(ContractFixtures.OBJECT_ID_ORDER).orElseThrow(), includeLineItems);
        };
    }

    private OrderStatusResponse buildOrderStatusResponse(RoutingScenario scenario) {
        return switch (scenario) {
            case UNMIGRATED -> orderTransformer.toStatusResponse(
                    orderJpaRepository.findById(ContractFixtures.UNMIGRATED_ORDER_ID).orElseThrow());
            case MIGRATED -> orderTransformer.toStatusResponse(
                    orderMongoRepository.findById(String.valueOf(ContractFixtures.MIGRATED_ORDER_ID)).orElseThrow());
            case NEW -> orderTransformer.toStatusResponse(
                    orderMongoRepository.findById(ContractFixtures.OBJECT_ID_ORDER).orElseThrow());
        };
    }

    private List<CustomerResponse> buildAllCustomersResponse() {
        List<CustomerResponse> responses = new ArrayList<>();
        customerJpaRepository.findByMigratedAtIsNull().stream()
                .map(customerTransformer::toResponse)
                .forEach(responses::add);
        customerMongoRepository.findAll().stream()
                .map(customerTransformer::toResponse)
                .forEach(responses::add);
        responses.sort(Comparator.comparing(customer -> String.valueOf(customer.getCustomerId())));
        return responses;
    }

    private List<OrderResponse> buildAllOrdersResponse() {
        List<OrderResponse> responses = new ArrayList<>();
        orderJpaRepository.findByMigratedAtIsNull().stream()
                .map(order -> orderTransformer.toResponse(order, List.of(), false))
                .forEach(responses::add);
        orderMongoRepository.findAll().stream()
                .map(document -> orderTransformer.toResponse(document, false))
                .forEach(responses::add);
        responses.sort(Comparator.comparing(order -> String.valueOf(order.getOrderId())));
        return responses;
    }

    private List<OrderResponse> buildCustomerOrdersResponse(int customerId) {
        List<OrderResponse> responses = new ArrayList<>();
        orderJpaRepository.findByMigratedAtIsNullAndCustomerId(customerId).stream()
                .map(order -> orderTransformer.toResponse(order, List.of(), false))
                .forEach(responses::add);
        orderMongoRepository.findByCustomerId(customerId).stream()
                .map(document -> orderTransformer.toResponse(document, false))
                .forEach(responses::add);
        responses.sort(Comparator.comparing(order -> String.valueOf(order.getOrderId())));
        return responses;
    }

    private List<OrderResponse> buildObjectIdCustomerOrdersResponse() {
        return List.of(buildOrderResponse(RoutingScenario.NEW, false));
    }
}
