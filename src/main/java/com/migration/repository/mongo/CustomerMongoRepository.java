package com.migration.repository.mongo;

import com.migration.model.mongo.CustomerDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CustomerMongoRepository extends MongoRepository<CustomerDocument, String> {
}
