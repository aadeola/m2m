/**
 * Transform tests verify deterministic mapping between JPA entities, Mongo documents, and legacy
 * DTOs via {@link com.migration.transform.CustomerTransformer},
 * {@link com.migration.transform.ProductTransformer}, and {@link com.migration.transform.OrderTransformer}.
 * Style: pure unit tests with explicit fixtures; no database required.
 */
package com.migration.transform;
