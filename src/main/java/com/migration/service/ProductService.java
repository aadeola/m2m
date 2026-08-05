package com.migration.service;

import com.migration.dto.ProductResponse;
import com.migration.exception.RecordNotFoundException;
import com.migration.model.jpa.ProductEntity;
import com.migration.model.mongo.ProductDocument;
import com.migration.repository.jpa.ProductJpaRepository;
import com.migration.repository.mongo.ProductMongoRepository;
import com.migration.routing.DataSource;
import com.migration.routing.DataSourceResolver;
import com.migration.routing.EntityType;
import com.migration.transform.ProductTransformer;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    private final DataSourceResolver dataSourceResolver;
    private final ProductJpaRepository productJpaRepository;
    private final ProductMongoRepository productMongoRepository;
    private final ProductTransformer productTransformer;

    public ProductService(
            DataSourceResolver dataSourceResolver,
            ProductJpaRepository productJpaRepository,
            ProductMongoRepository productMongoRepository,
            ProductTransformer productTransformer) {
        this.dataSourceResolver = dataSourceResolver;
        this.productJpaRepository = productJpaRepository;
        this.productMongoRepository = productMongoRepository;
        this.productTransformer = productTransformer;
    }

    public ProductResponse getProductById(String id) {
        DataSource dataSource = dataSourceResolver.resolveDataSource(EntityType.PRODUCT, id);
        if (dataSource == DataSource.POSTGRES) {
            ProductEntity entity = productJpaRepository.findById(Integer.parseInt(id))
                    .orElseThrow(() -> new RecordNotFoundException("Product not found: " + id));
            return productTransformer.toResponse(entity);
        }
        ProductDocument document = productMongoRepository.findById(id)
                .orElseThrow(() -> new RecordNotFoundException("Product not found: " + id));
        return productTransformer.toResponse(document);
    }

    ProductEntity requireProductForOrderCreation(Integer productId) {
        DataSource dataSource = dataSourceResolver.resolveDataSource(
                EntityType.PRODUCT, String.valueOf(productId));
        if (dataSource == DataSource.POSTGRES) {
            return productJpaRepository.findById(productId)
                    .orElseThrow(() -> new RecordNotFoundException("Product not found: " + productId));
        }
        ProductDocument document = productMongoRepository.findById(String.valueOf(productId))
                .orElseThrow(() -> new RecordNotFoundException("Product not found: " + productId));
        ProductEntity entity = new ProductEntity();
        entity.setProductId(productId);
        entity.setName(document.getName());
        entity.setSku(document.getSku());
        entity.setPrice(document.getPrice());
        return entity;
    }
}
