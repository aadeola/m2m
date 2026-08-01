package com.migration.repository.mongo;

import com.migration.model.mongo.OrderDocument;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrderMongoRepository extends MongoRepository<OrderDocument, String> {

    List<OrderDocument> findByCustomerId(Integer customerId);

    List<OrderDocument> findByCustomer_CustomerId(String customerId);
}
