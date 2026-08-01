package com.migration.contract.support;

/**
 * Inventory call-site metadata used to parameterize contract tests.
 */
public record InventoryCallSite(String endpoint, String method, String owningService, String sourceFile) {

    public static InventoryCallSite of(String endpoint, String method, String owningService, String sourceFile) {
        return new InventoryCallSite(endpoint, method, owningService, sourceFile);
    }
}
