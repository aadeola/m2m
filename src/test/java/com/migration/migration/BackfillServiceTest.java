package com.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.migration.model.jpa.BackfillCheckpointEntity;
import com.migration.model.jpa.CustomerEntity;
import com.migration.model.jpa.LineItemEntity;
import com.migration.model.jpa.OrderEntity;
import com.migration.model.jpa.ProductEntity;
import com.migration.model.mongo.CustomerDocument;
import com.migration.model.mongo.OrderDocument;
import com.migration.quality.CustomerInvalidFieldTagger;
import com.migration.repository.jpa.BackfillCheckpointRepository;
import com.migration.repository.jpa.BackfillDlqRepository;
import com.migration.repository.jpa.CustomerJpaRepository;
import com.migration.repository.jpa.LineItemJpaRepository;
import com.migration.repository.jpa.OrderJpaRepository;
import com.migration.repository.jpa.ProductJpaRepository;
import com.migration.service.BackfillService;
import com.migration.transform.CustomerTransformer;
import com.migration.transform.OrderTransformer;
import com.migration.transform.ProductTransformer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class BackfillServiceTest {

    @Mock
    private CustomerJpaRepository customerJpaRepository;

    @Mock
    private ProductJpaRepository productJpaRepository;

    @Mock
    private OrderJpaRepository orderJpaRepository;

    @Mock
    private LineItemJpaRepository lineItemJpaRepository;

    @Mock
    private BackfillCheckpointRepository checkpointRepository;

    @Mock
    private BackfillDlqRepository backfillDlqRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private CustomerInvalidFieldTagger customerInvalidFieldTagger;

    @Mock
    private BulkOperations bulkOperations;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private PlatformTransactionManager transactionManager;

    private BackfillService backfillService;

    @BeforeEach
    void setUp() {
        backfillService = new BackfillService(
                10,
                customerJpaRepository,
                productJpaRepository,
                orderJpaRepository,
                lineItemJpaRepository,
                checkpointRepository,
                backfillDlqRepository,
                mongoTemplate,
                new CustomerTransformer(),
                customerInvalidFieldTagger,
                new ProductTransformer(),
                new OrderTransformer(),
                transactionTemplate,
                transactionManager);

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });

        lenient().when(mongoTemplate.bulkOps(any(), eq(CustomerDocument.class))).thenReturn(bulkOperations);
        lenient().when(bulkOperations.replaceOne(any(), any(), any())).thenReturn(bulkOperations);
    }

    @Test
    void runBackfill_migratesCustomerBatchAndUpdatesCheckpoint() {
        CustomerEntity customer = new CustomerEntity();
        customer.setCustomerId(42);
        customer.setFirstName("Alice");
        customer.setLastName("Smith");
        customer.setAccountNumber("CUS0042");
        customer.setPhoneNumber("5550000042");
        customer.setEmail("alice@example.com");

        BackfillCheckpointEntity checkpoint = new BackfillCheckpointEntity();
        checkpoint.setEntityName("customers");
        checkpoint.setLastProcessedPk(0);

        when(customerJpaRepository.findByMigratedAtIsNullOrderByCustomerIdAsc(any()))
                .thenReturn(List.of(customer))
                .thenReturn(List.of());
        when(productJpaRepository.findByMigratedAtIsNullOrderByProductIdAsc(any()))
                .thenReturn(List.of());
        when(orderJpaRepository.findByMigratedAtIsNullOrderByOrderIdAsc(any()))
                .thenReturn(List.of());
        when(checkpointRepository.findById("customers")).thenReturn(Optional.of(checkpoint));

        backfillService.runBackfill();

        assertNotNull(customer.getMigratedAt());

        ArgumentCaptor<List<CustomerEntity>> savedCustomers = ArgumentCaptor.forClass(List.class);
        verify(customerJpaRepository).saveAll(savedCustomers.capture());
        verify(mongoTemplate).bulkOps(any(), eq(CustomerDocument.class));
        verify(bulkOperations).execute();
        verify(customerInvalidFieldTagger).tagAfterMigration(any());
        verify(checkpointRepository).save(checkpoint);
    }

    @Test
    void runBackfill_embedsUnmigratedProductsInOrderLineItems() {
        OrderEntity order = new OrderEntity();
        order.setOrderId(100);
        order.setCustomerId(1);
        order.setOrderDate(LocalDate.of(2025, 3, 1));
        order.setStatus("SHIPPED");
        order.setTotalAmount(new BigDecimal("99.99"));

        CustomerEntity customer = new CustomerEntity();
        customer.setCustomerId(1);
        customer.setFirstName("Alice");
        customer.setLastName("Smith");
        customer.setAccountNumber("CUS0001");
        customer.setPhoneNumber("5550000001");
        customer.setEmail("alice@example.com");

        ProductEntity product = new ProductEntity();
        product.setProductId(37);
        product.setName("Locked Widget");
        product.setSku("SKU-37");
        product.setPrice(new BigDecimal("19.99"));

        LineItemEntity lineItem = new LineItemEntity();
        lineItem.setLineItemId(524374);
        lineItem.setOrderId(100);
        lineItem.setProductId(37);
        lineItem.setQuantity(1);
        lineItem.setUnitPrice(new BigDecimal("19.99"));

        when(customerJpaRepository.findByMigratedAtIsNullOrderByCustomerIdAsc(any()))
                .thenReturn(List.of());
        when(productJpaRepository.findByMigratedAtIsNullOrderByProductIdAsc(any()))
                .thenReturn(List.of());
        when(orderJpaRepository.findByMigratedAtIsNullOrderByOrderIdAsc(any()))
                .thenReturn(List.of(order))
                .thenReturn(List.of());
        when(lineItemJpaRepository.findByOrderIdOrderByLineItemIdAsc(100)).thenReturn(List.of(lineItem));
        when(customerJpaRepository.findById(1)).thenReturn(Optional.of(customer));
        when(productJpaRepository.findById(37)).thenReturn(Optional.of(product));
        when(mongoTemplate.bulkOps(any(), eq(OrderDocument.class))).thenReturn(bulkOperations);

        backfillService.runBackfill();

        ArgumentCaptor<OrderDocument> orderDocumentCaptor = ArgumentCaptor.forClass(OrderDocument.class);
        verify(bulkOperations).replaceOne(any(), orderDocumentCaptor.capture(), any());
        OrderDocument document = orderDocumentCaptor.getValue();
        assertEquals(1, document.getLineItems().size());
        assertNotNull(document.getLineItems().getFirst().getProduct());
        assertEquals(37, document.getLineItems().getFirst().getProduct().getProductId());
    }
}
