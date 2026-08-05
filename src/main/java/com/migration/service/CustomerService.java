package com.migration.service;

import com.migration.dto.CustomerResponse;
import com.migration.exception.RecordNotFoundException;
import com.migration.model.jpa.CustomerEntity;
import com.migration.model.mongo.CustomerDocument;
import com.migration.repository.jpa.CustomerJpaRepository;
import com.migration.repository.mongo.CustomerMongoRepository;
import com.migration.routing.DataSource;
import com.migration.routing.DataSourceResolver;
import com.migration.routing.EntityType;
import com.migration.transform.CustomerTransformer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {

    private final DataSourceResolver dataSourceResolver;
    private final CustomerJpaRepository customerJpaRepository;
    private final CustomerMongoRepository customerMongoRepository;
    private final CustomerTransformer customerTransformer;

    public CustomerService(
            DataSourceResolver dataSourceResolver,
            CustomerJpaRepository customerJpaRepository,
            CustomerMongoRepository customerMongoRepository,
            CustomerTransformer customerTransformer) {
        this.dataSourceResolver = dataSourceResolver;
        this.customerJpaRepository = customerJpaRepository;
        this.customerMongoRepository = customerMongoRepository;
        this.customerTransformer = customerTransformer;
    }

    public List<CustomerResponse> getAllCustomers() {
        List<CustomerResponse> responses = new ArrayList<>();
        customerJpaRepository.findByMigratedAtIsNull().stream()
                .map(customerTransformer::toResponse)
                .forEach(responses::add);
        customerMongoRepository.findAll().stream()
                .map(customerTransformer::toResponse)
                .forEach(responses::add);
        responses.sort(Comparator.comparing(r -> String.valueOf(r.getCustomerId())));
        return responses;
    }

    public CustomerResponse getCustomerById(String id) {
        DataSource dataSource = dataSourceResolver.resolveDataSource(EntityType.CUSTOMER, id);
        if (dataSource == DataSource.POSTGRES) {
            CustomerEntity entity = customerJpaRepository.findById(Integer.parseInt(id))
                    .orElseThrow(() -> new RecordNotFoundException("Customer not found: " + id));
            return customerTransformer.toResponse(entity);
        }
        CustomerDocument document = customerMongoRepository.findById(id)
                .orElseThrow(() -> new RecordNotFoundException("Customer not found: " + id));
        return customerTransformer.toResponse(document);
    }

    CustomerEntity requirePostgresCustomer(Integer customerId) {
        return customerJpaRepository.findById(customerId)
                .orElseThrow(() -> new RecordNotFoundException("Customer not found: " + customerId));
    }

    CustomerEntity requireCustomerForOrderCreation(Integer customerId) {
        DataSource dataSource = dataSourceResolver.resolveDataSource(
                EntityType.CUSTOMER, String.valueOf(customerId));
        if (dataSource == DataSource.POSTGRES) {
            return requirePostgresCustomer(customerId);
        }
        CustomerDocument document = customerMongoRepository.findById(String.valueOf(customerId))
                .orElseThrow(() -> new RecordNotFoundException("Customer not found: " + customerId));
        CustomerEntity entity = new CustomerEntity();
        entity.setCustomerId(customerId);
        entity.setFirstName(document.getFirstName());
        entity.setLastName(document.getLastName());
        entity.setAccountNumber(document.getAccountNumber());
        entity.setPhoneNumber(document.getPhoneNumber());
        entity.setEmail(document.getEmail());
        entity.setCreatedAt(document.getCreatedAt());
        return entity;
    }
}
