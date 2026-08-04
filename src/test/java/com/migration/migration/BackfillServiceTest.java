package com.migration.migration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.migration.model.jpa.BackfillCheckpointEntity;
import com.migration.model.jpa.CustomerEntity;
import com.migration.model.mongo.CustomerDocument;
import com.migration.repository.jpa.BackfillCheckpointRepository;
import com.migration.repository.jpa.CustomerJpaRepository;
import com.migration.repository.jpa.LineItemJpaRepository;
import com.migration.repository.jpa.OrderJpaRepository;
import com.migration.repository.jpa.ProductJpaRepository;
import com.migration.service.BackfillService;
import com.migration.transform.CustomerTransformer;
import com.migration.transform.OrderTransformer;
import com.migration.transform.ProductTransformer;
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
    private MongoTemplate mongoTemplate;

    @Mock
    private BulkOperations bulkOperations;

    @Mock
    private TransactionTemplate transactionTemplate;

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
                mongoTemplate,
                new CustomerTransformer(),
                new ProductTransformer(),
                new OrderTransformer(),
                transactionTemplate);

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });

        when(mongoTemplate.bulkOps(any(), eq(CustomerDocument.class))).thenReturn(bulkOperations);
        when(bulkOperations.replaceOne(any(), any(), any())).thenReturn(bulkOperations);
    }

    @Test
    void runBackfill_migratesCustomerBatchAndUpdatesCheckpoint() {
        CustomerEntity customer = new CustomerEntity();
        customer.setCustomerId(42);
        customer.setName("Alice");
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
        verify(checkpointRepository).save(checkpoint);
    }
}
