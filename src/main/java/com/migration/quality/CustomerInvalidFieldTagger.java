package com.migration.quality;

import com.migration.model.mongo.CustomerDocument;
import com.migration.repository.mongo.CustomerMongoRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
public class CustomerInvalidFieldTagger {

    private static final Pattern CUSTOMER_ID_IN_LOG = Pattern.compile(
            "customers\\s+\\{\\s*_id:\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);

    private final CustomerDataQualityRules qualityRules;
    private final CustomerMongoRepository customerMongoRepository;
    private final MongoTemplate mongoTemplate;

    public CustomerInvalidFieldTagger(
            CustomerDataQualityRules qualityRules,
            CustomerMongoRepository customerMongoRepository,
            MongoTemplate mongoTemplate) {
        this.qualityRules = qualityRules;
        this.customerMongoRepository = customerMongoRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public void tagAfterMigration(List<CustomerDocument> documents) {
        for (CustomerDocument document : documents) {
            tagDocument(document.getId(), qualityRules.detectInvalidFields(document));
        }
    }

    public void tagFromMongoLogs(String logContent) {
        Set<String> customerIds = new LinkedHashSet<>();
        Matcher matcher = CUSTOMER_ID_IN_LOG.matcher(logContent);
        while (matcher.find()) {
            customerIds.add(matcher.group(1));
        }

        for (String customerId : customerIds) {
            CustomerDocument document = customerMongoRepository.findById(customerId).orElse(null);
            if (document == null) {
                continue;
            }
            tagDocument(customerId, qualityRules.detectInvalidFields(document));
        }
    }

    public void tagDocument(String customerId, List<String> invalidFields) {
        Query query = Query.query(Criteria.where("_id").is(customerId));
        if (invalidFields == null || invalidFields.isEmpty()) {
            mongoTemplate.updateFirst(query, new Update().unset("invalidFields"), CustomerDocument.class);
            return;
        }
        mongoTemplate.updateFirst(
                query,
                new Update().set("invalidFields", new ArrayList<>(invalidFields)),
                CustomerDocument.class);
    }
}
