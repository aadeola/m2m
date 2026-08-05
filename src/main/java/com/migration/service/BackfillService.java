package com.migration.service;

import com.migration.exception.MongoSchemaValidationException;
import com.migration.exception.MongoSchemaValidationFailures;
import com.migration.model.jpa.BackfillCheckpointEntity;
import com.migration.model.jpa.BackfillDlqEntity;
import com.migration.model.jpa.CustomerEntity;
import com.migration.model.jpa.LineItemEntity;
import com.migration.model.jpa.OrderEntity;
import com.migration.model.jpa.ProductEntity;
import com.migration.model.mongo.CustomerDocument;
import com.migration.model.mongo.OrderDocument;
import com.migration.model.mongo.ProductDocument;
import com.migration.repository.jpa.BackfillCheckpointRepository;
import com.migration.repository.jpa.BackfillDlqRepository;
import com.migration.repository.jpa.CustomerJpaRepository;
import com.migration.repository.jpa.LineItemJpaRepository;
import com.migration.repository.jpa.OrderJpaRepository;
import com.migration.repository.jpa.ProductJpaRepository;
import com.migration.quality.CustomerInvalidFieldTagger;
import com.migration.transform.CustomerTransformer;
import com.migration.transform.OrderTransformer;
import com.migration.transform.ProductTransformer;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BackfillService {

    private static final Logger log = LoggerFactory.getLogger(BackfillService.class);

    private static final String CUSTOMERS = "customers";
    private static final String PRODUCTS = "products";
    private static final String ORDERS = "orders";

    private final int batchSize;
    private final CustomerJpaRepository customerJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final OrderJpaRepository orderJpaRepository;
    private final LineItemJpaRepository lineItemJpaRepository;
    private final BackfillCheckpointRepository checkpointRepository;
    private final BackfillDlqRepository backfillDlqRepository;
    private final MongoTemplate mongoTemplate;
    private final CustomerTransformer customerTransformer;
    private final CustomerInvalidFieldTagger customerInvalidFieldTagger;
    private final ProductTransformer productTransformer;
    private final OrderTransformer orderTransformer;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public BackfillService(
            @Value("${migration.backfill.batch-size:10}") int batchSize,
            CustomerJpaRepository customerJpaRepository,
            ProductJpaRepository productJpaRepository,
            OrderJpaRepository orderJpaRepository,
            LineItemJpaRepository lineItemJpaRepository,
            BackfillCheckpointRepository checkpointRepository,
            BackfillDlqRepository backfillDlqRepository,
            MongoTemplate mongoTemplate,
            CustomerTransformer customerTransformer,
            CustomerInvalidFieldTagger customerInvalidFieldTagger,
            ProductTransformer productTransformer,
            OrderTransformer orderTransformer,
            TransactionTemplate transactionTemplate,
            PlatformTransactionManager transactionManager) {
        this.batchSize = batchSize;
        this.customerJpaRepository = customerJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.orderJpaRepository = orderJpaRepository;
        this.lineItemJpaRepository = lineItemJpaRepository;
        this.checkpointRepository = checkpointRepository;
        this.backfillDlqRepository = backfillDlqRepository;
        this.mongoTemplate = mongoTemplate;
        this.customerTransformer = customerTransformer;
        this.customerInvalidFieldTagger = customerInvalidFieldTagger;
        this.productTransformer = productTransformer;
        this.orderTransformer = orderTransformer;
        this.transactionTemplate = transactionTemplate;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void runBackfill() {
        log.info("Starting backfill job (batch size={})", batchSize);
        migrateCustomers();
        migrateProducts();
        migrateOrders();
        log.info("Backfill job complete");
    }

    private void migrateCustomers() {
        int totalMigrated = 0;
        int cursor = startingCursor(CUSTOMERS);
        List<CustomerEntity> batch;
        do {
            batch = customerJpaRepository.findByMigratedAtIsNullAndCustomerIdGreaterThanOrderByCustomerIdAsc(
                    cursor, PageRequest.of(0, batchSize));
            if (batch.isEmpty()) {
                break;
            }
            final List<CustomerEntity> currentBatch = batch;
            int firstPk = currentBatch.get(0).getCustomerId();
            int lastPk = currentBatch.get(currentBatch.size() - 1).getCustomerId();
            try {
                int migratedInBatch = transactionTemplate.execute(status -> migrateCustomerBatch(currentBatch));
                totalMigrated += migratedInBatch;
                log.info("Migrated {} customers in batch pk {}-{}; {} customers total",
                        migratedInBatch, firstPk, lastPk, totalMigrated);
            } catch (Exception ex) {
                recordFailure(CUSTOMERS, firstPk, lastPk, ex);
                advanceCheckpointPastFailure(CUSTOMERS, lastPk);
                logBatchFailure("CustomerDocument", firstPk, lastPk, ex);
            }
            cursor = lastPk;
        } while (batch.size() == batchSize);
        log.info("Migrated {} customers", totalMigrated);
    }

    private int migrateCustomerBatch(List<CustomerEntity> batch) {
        List<CustomerDocument> documents = batch.stream()
                .map(customerTransformer::toDocument)
                .toList();
        bulkUpsert(CustomerDocument.class, documents);
        customerInvalidFieldTagger.tagAfterMigration(documents);

        LocalDateTime migratedAt = LocalDateTime.now();
        int maxPk = 0;
        for (CustomerEntity entity : batch) {
            entity.setMigratedAt(migratedAt);
            maxPk = Math.max(maxPk, entity.getCustomerId());
        }
        customerJpaRepository.saveAll(batch);
        updateCheckpoint(CUSTOMERS, maxPk);
        return batch.size();
    }

    private void migrateProducts() {
        int totalMigrated = 0;
        int cursor = startingCursor(PRODUCTS);
        List<ProductEntity> batch;
        do {
            batch = productJpaRepository.findByMigratedAtIsNullAndProductIdGreaterThanOrderByProductIdAsc(
                    cursor, PageRequest.of(0, batchSize));
            if (batch.isEmpty()) {
                break;
            }
            final List<ProductEntity> currentBatch = batch;
            int firstPk = currentBatch.get(0).getProductId();
            int lastPk = currentBatch.get(currentBatch.size() - 1).getProductId();
            try {
                int migratedInBatch = transactionTemplate.execute(status -> migrateProductBatch(currentBatch));
                totalMigrated += migratedInBatch;
                log.info("Migrated {} products in batch pk {}-{}; {} products total",
                        migratedInBatch, firstPk, lastPk, totalMigrated);
            } catch (Exception ex) {
                recordFailure(PRODUCTS, firstPk, lastPk, ex);
                advanceCheckpointPastFailure(PRODUCTS, lastPk);
                logBatchFailure("ProductDocument", firstPk, lastPk, ex);
            }
            cursor = lastPk;
        } while (batch.size() == batchSize);
        log.info("Migrated {} products", totalMigrated);
    }

    private int migrateProductBatch(List<ProductEntity> batch) {
        List<ProductDocument> documents = batch.stream()
                .map(productTransformer::toDocument)
                .toList();
        bulkUpsert(ProductDocument.class, documents);

        LocalDateTime migratedAt = LocalDateTime.now();
        int maxPk = 0;
        for (ProductEntity entity : batch) {
            entity.setMigratedAt(migratedAt);
            maxPk = Math.max(maxPk, entity.getProductId());
        }
        productJpaRepository.saveAll(batch);
        updateCheckpoint(PRODUCTS, maxPk);
        return batch.size();
    }

    private void migrateOrders() {
        int totalMigrated = 0;
        int cursor = startingCursor(ORDERS);
        List<OrderEntity> batch;
        do {
            batch = orderJpaRepository.findByMigratedAtIsNullAndOrderIdGreaterThanOrderByOrderIdAsc(
                    cursor, PageRequest.of(0, batchSize));
            if (batch.isEmpty()) {
                break;
            }
            final List<OrderEntity> currentBatch = batch;
            int firstPk = currentBatch.get(0).getOrderId();
            int lastPk = currentBatch.get(currentBatch.size() - 1).getOrderId();
            try {
                int migratedInBatch = transactionTemplate.execute(status -> migrateOrderBatch(currentBatch));
                totalMigrated += migratedInBatch;
                log.info("Migrated {} orders in batch pk {}-{}; {} orders total",
                        migratedInBatch, firstPk, lastPk, totalMigrated);
            } catch (Exception ex) {
                recordFailure(ORDERS, firstPk, lastPk, ex);
                advanceCheckpointPastFailure(ORDERS, lastPk);
                logBatchFailure("OrderDocument", firstPk, lastPk, ex);
            }
            cursor = lastPk;
        } while (batch.size() == batchSize);
        log.info("Migrated {} orders", totalMigrated);
    }

    private int migrateOrderBatch(List<OrderEntity> batch) {
        List<OrderDocument> documents = batch.stream()
                .map(this::toOrderDocument)
                .toList();
        bulkUpsert(OrderDocument.class, documents);

        LocalDateTime migratedAt = LocalDateTime.now();
        int maxPk = 0;
        for (OrderEntity entity : batch) {
            entity.setMigratedAt(migratedAt);
            maxPk = Math.max(maxPk, entity.getOrderId());
        }
        orderJpaRepository.saveAll(batch);
        updateCheckpoint(ORDERS, maxPk);
        return batch.size();
    }

    private OrderDocument toOrderDocument(OrderEntity order) {
        List<LineItemEntity> lineItems =
                lineItemJpaRepository.findByOrderIdOrderByLineItemIdAsc(order.getOrderId());
        CustomerEntity customer = customerJpaRepository.findById(order.getCustomerId())
                .orElseThrow(() -> new IllegalStateException(
                        "Missing customer for order " + order.getOrderId()));

        Map<Integer, ProductEntity> productsById = new HashMap<>();
        for (LineItemEntity lineItem : lineItems) {
            // Only embed products that have finished migrating, to respect migration ordering.
            productJpaRepository.findById(lineItem.getProductId())
                    .filter(product -> product.getMigratedAt() != null)
                    .ifPresent(product -> productsById.put(lineItem.getProductId(), product));
        }

        return orderTransformer.toDocument(order, lineItems, customer, productsById);
    }

    private void recordFailure(String entityName, int startPk, int endPk, Exception ex) {
        requiresNewTransactionTemplate.executeWithoutResult(status -> {
            BackfillDlqEntity failure = new BackfillDlqEntity();
            failure.setEntityName(entityName);
            failure.setStartPk(startPk);
            failure.setEndPk(endPk);
            failure.setExceptionClass(ex.getClass().getName());
            failure.setMessage(ex.getMessage());
            failure.setOccurredAt(LocalDateTime.now());
            failure.setResolved(false);
            backfillDlqRepository.save(failure);
        });
    }

    private <T> void bulkUpsert(Class<T> documentClass, List<T> documents) {
        if (documents.isEmpty()) {
            return;
        }
        try {
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, documentClass);
            FindAndReplaceOptions upsert = FindAndReplaceOptions.options().upsert();
            for (T document : documents) {
                String id = extractId(document);
                Query query = new Query(Criteria.where("_id").is(id));
                bulkOps.replaceOne(query, document, upsert);
            }
            bulkOps.execute();
        } catch (RuntimeException ex) {
            throw MongoSchemaValidationFailures.wrapIfSchemaValidation(documentClass, ex);
        }
    }

    private String extractId(Object document) {
        if (document instanceof CustomerDocument customerDocument) {
            return customerDocument.getId();
        }
        if (document instanceof ProductDocument productDocument) {
            return productDocument.getId();
        }
        if (document instanceof OrderDocument orderDocument) {
            return orderDocument.getId();
        }
        throw new IllegalArgumentException("Unsupported document type: " + document.getClass());
    }

    private void updateCheckpoint(String entityName, int lastProcessedPk) {
        BackfillCheckpointEntity checkpoint = checkpointRepository.findById(entityName)
                .orElseThrow(() -> new IllegalStateException("Missing checkpoint for " + entityName));
        checkpoint.setLastProcessedPk(Math.max(checkpoint.getLastProcessedPk(), lastProcessedPk));
        checkpointRepository.save(checkpoint);
    }

    private int startingCursor(String entityName) {
        return checkpointRepository.findById(entityName)
                .map(BackfillCheckpointEntity::getLastProcessedPk)
                .orElse(0);
    }

    private void advanceCheckpointPastFailure(String entityName, int lastProcessedPk) {
        requiresNewTransactionTemplate.executeWithoutResult(status -> updateCheckpoint(entityName, lastProcessedPk));
    }

    private void logBatchFailure(String documentType, int firstPk, int lastPk, Exception ex) {
        if (ex instanceof MongoSchemaValidationException) {
            log.error("MongoDB schema validation failed writing {}, pk {}-{}; recorded to DLQ and continuing",
                    documentType, firstPk, lastPk);
        } else {
            log.error("{} batch pk {}-{} failed ({}); recorded to DLQ and continuing",
                    documentType, firstPk, lastPk, ex.getClass().getSimpleName());
        }
    }
}
