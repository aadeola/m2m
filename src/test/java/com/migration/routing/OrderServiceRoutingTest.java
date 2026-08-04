package com.migration.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.migration.dto.OrderResponse;
import com.migration.model.jpa.OrderEntity;
import com.migration.model.mongo.OrderDocument;
import com.migration.repository.jpa.CustomerJpaRepository;
import com.migration.repository.jpa.LineItemJpaRepository;
import com.migration.repository.jpa.OrderJpaRepository;
import com.migration.repository.jpa.ProductJpaRepository;
import com.migration.repository.mongo.OrderMongoRepository;
import com.migration.service.OrderService;
import com.migration.transform.OrderTransformer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceRoutingTest {

    private static final String OBJECT_ID_CUSTOMER = "507f191e810c19729de860eb";

    @Mock
    private OrderJpaRepository orderJpaRepository;

    @Mock
    private LineItemJpaRepository lineItemJpaRepository;

    @Mock
    private CustomerJpaRepository customerJpaRepository;

    @Mock
    private ProductJpaRepository productJpaRepository;

    @Mock
    private OrderMongoRepository orderMongoRepository;

    private final OrderTransformer orderTransformer = new OrderTransformer();

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        DataSourceResolver resolver =
                new DataSourceResolver(customerJpaRepository, productJpaRepository, orderJpaRepository);
        orderService = new OrderService(
                resolver,
                orderJpaRepository,
                lineItemJpaRepository,
                orderMongoRepository,
                null,
                null,
                orderTransformer);
    }

    @Test
    void getOrdersByCustomerId_objectIdQueriesMongoOnly() {
        OrderDocument document = new OrderDocument();
        document.setId("99");
        document.setCustomerId(1);
        document.setOrderDate(LocalDate.of(2025, 3, 1));
        document.setStatus("PENDING");
        document.setTotalAmount(new BigDecimal("10.00"));

        when(orderMongoRepository.findByCustomer_CustomerId(OBJECT_ID_CUSTOMER)).thenReturn(List.of(document));

        List<OrderResponse> results = orderService.getOrdersByCustomerId(OBJECT_ID_CUSTOMER);

        assertEquals(1, results.size());
        assertEquals(99, results.getFirst().getOrderId());
    }

    @Test
    void mergeOrderLists_combinesUnmigratedPostgresAndAllMongo() {
        OrderEntity postgresOrder = new OrderEntity();
        postgresOrder.setOrderId(1);
        postgresOrder.setCustomerId(7);
        postgresOrder.setOrderDate(LocalDate.of(2025, 1, 1));
        postgresOrder.setStatus("PENDING");
        postgresOrder.setTotalAmount(new BigDecimal("10.00"));

        OrderDocument mongoOrder = new OrderDocument();
        mongoOrder.setId("2");
        mongoOrder.setCustomerId(7);
        mongoOrder.setOrderDate(LocalDate.of(2025, 2, 1));
        mongoOrder.setStatus("SHIPPED");
        mongoOrder.setTotalAmount(new BigDecimal("20.00"));

        when(orderJpaRepository.findByMigratedAtIsNull()).thenReturn(List.of(postgresOrder));
        when(orderMongoRepository.findAll()).thenReturn(new ArrayList<>(List.of(mongoOrder)));

        List<OrderResponse> results = orderService.getAllOrders();

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(r -> r.getOrderId().equals(1)));
        assertTrue(results.stream().anyMatch(r -> r.getOrderId().equals(2)));
    }
}
