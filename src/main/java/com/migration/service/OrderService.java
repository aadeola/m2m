package com.migration.service;

import com.migration.debug.AgentDebugLog;
import com.migration.dto.CreateOrderRequest;
import com.migration.dto.OrderResponse;
import com.migration.dto.OrderStatusResponse;
import com.migration.exception.RecordNotFoundException;
import com.migration.model.jpa.CustomerEntity;
import com.migration.model.jpa.LineItemEntity;
import com.migration.model.jpa.OrderEntity;
import com.migration.model.jpa.ProductEntity;
import com.migration.model.mongo.OrderDocument;
import com.migration.repository.jpa.LineItemJpaRepository;
import com.migration.repository.jpa.OrderJpaRepository;
import com.migration.repository.mongo.OrderMongoRepository;
import com.migration.routing.DataSource;
import com.migration.routing.DataSourceResolver;
import com.migration.routing.EntityType;
import com.migration.transform.OrderTransformer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final DataSourceResolver dataSourceResolver;
    private final OrderJpaRepository orderJpaRepository;
    private final LineItemJpaRepository lineItemJpaRepository;
    private final OrderMongoRepository orderMongoRepository;
    private final CustomerService customerService;
    private final ProductService productService;
    private final OrderTransformer orderTransformer;

    public OrderService(
            DataSourceResolver dataSourceResolver,
            OrderJpaRepository orderJpaRepository,
            LineItemJpaRepository lineItemJpaRepository,
            OrderMongoRepository orderMongoRepository,
            CustomerService customerService,
            ProductService productService,
            OrderTransformer orderTransformer) {
        this.dataSourceResolver = dataSourceResolver;
        this.orderJpaRepository = orderJpaRepository;
        this.lineItemJpaRepository = lineItemJpaRepository;
        this.orderMongoRepository = orderMongoRepository;
        this.customerService = customerService;
        this.productService = productService;
        this.orderTransformer = orderTransformer;
    }

    public OrderResponse getOrderById(String id) {
        DataSource dataSource = dataSourceResolver.resolveDataSource(EntityType.ORDER, id);
        // #region agent log
        AgentDebugLog.log("C,D", "OrderService.getOrderById", "read branch",
                "{\"id\":\"" + id + "\",\"dataSource\":\"" + dataSource + "\"}");
        // #endregion
        if (dataSource == DataSource.POSTGRES) {
            return loadPostgresOrder(Integer.parseInt(id), true);
        }
        OrderDocument document = orderMongoRepository.findById(id)
                .orElseThrow(() -> orderNotFound(id));
        return orderTransformer.toResponse(document, true);
    }

    public List<OrderResponse> getAllOrders() {
        return mergeOrderLists(null);
    }

    public List<OrderResponse> getOrdersByCustomerId(Integer customerId) {
        return getOrdersByCustomerId(String.valueOf(customerId));
    }

    public List<OrderResponse> getOrdersByCustomerId(String customerId) {
        if (DataSourceResolver.isObjectId(customerId)) {
            return orderMongoRepository.findByCustomer_CustomerId(customerId).stream()
                    .map(document -> orderTransformer.toResponse(document, false))
                    .sorted(Comparator.comparing(r -> String.valueOf(r.getOrderId())))
                    .toList();
        }
        if (!DataSourceResolver.isNumericId(customerId)) {
            throw new IllegalArgumentException("Invalid customer id: " + customerId);
        }
        return mergeOrderLists(Integer.parseInt(customerId));
    }

    public OrderStatusResponse getOrderStatus(String id) {
        DataSource dataSource = dataSourceResolver.resolveDataSource(EntityType.ORDER, id);
        if (dataSource == DataSource.POSTGRES) {
            OrderEntity order = orderJpaRepository.findById(Integer.parseInt(id))
                    .orElseThrow(() -> orderNotFound(id));
            return orderTransformer.toStatusResponse(order);
        }
        OrderDocument document = orderMongoRepository.findById(id)
                .orElseThrow(() -> orderNotFound(id));
        return orderTransformer.toStatusResponse(document);
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        if (request.getCustomerId() == null || request.getLineItems() == null || request.getLineItems().isEmpty()) {
            throw new IllegalArgumentException("customer_id and line_items are required");
        }

        CustomerEntity customer = customerService.requireCustomerForOrderCreation(request.getCustomerId());
        Map<Integer, ProductEntity> productsById = new HashMap<>();
        for (var lineItem : request.getLineItems()) {
            productsById.put(
                    lineItem.getProductId(),
                    productService.requireProductForOrderCreation(lineItem.getProductId()));
        }

        String objectId = new ObjectId().toHexString();
        dataSourceResolver.resolveDataSource(EntityType.ORDER, objectId);

        OrderDocument document = orderTransformer.toDocument(objectId, request, customer, productsById);
        OrderDocument saved = orderMongoRepository.save(document);
        return orderTransformer.toResponse(saved, true);
    }

    private OrderResponse loadPostgresOrder(Integer orderId, boolean includeLineItems) {
        OrderEntity order = orderJpaRepository.findById(orderId)
                .orElseThrow(() -> orderNotFound(orderId));
        List<LineItemEntity> lineItems = includeLineItems
                ? lineItemJpaRepository.findByOrderIdOrderByLineItemIdAsc(orderId)
                : List.of();
        return orderTransformer.toResponse(order, lineItems, includeLineItems);
    }

    private static RecordNotFoundException orderNotFound(Object id) {
        log.info("Order not found: {}", id);
        return new RecordNotFoundException("Order not found: " + id);
    }

    private List<OrderResponse> mergeOrderLists(Integer customerId) {
        List<OrderResponse> responses = new ArrayList<>();

        List<OrderEntity> postgresOrders = customerId == null
                ? orderJpaRepository.findByMigratedAtIsNull()
                : orderJpaRepository.findByMigratedAtIsNullAndCustomerId(customerId);
        for (OrderEntity order : postgresOrders) {
            responses.add(orderTransformer.toResponse(order, List.of(), false));
        }

        List<OrderDocument> mongoOrders = customerId == null
                ? orderMongoRepository.findAll()
                : orderMongoRepository.findByCustomerId(customerId);
        // #region agent log
        AgentDebugLog.log("E", "OrderService.mergeOrderLists", "list merge counts",
                "{\"customerId\":" + (customerId == null ? "null" : customerId)
                        + ",\"postgresCount\":" + postgresOrders.size()
                        + ",\"mongoCount\":" + mongoOrders.size() + "}");
        // #endregion
        for (OrderDocument document : mongoOrders) {
            responses.add(orderTransformer.toResponse(document, false));
        }

        responses.sort(Comparator.comparing(r -> String.valueOf(r.getOrderId())));
        return responses;
    }
}
