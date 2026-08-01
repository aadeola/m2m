package com.migration.contract.support;

import java.util.List;

/**
 * Inventory call sites from inventory.json grouped by unique endpoint contract.
 */
public final class InventoryCatalog {

    private InventoryCatalog() {
    }

    public static List<InventoryCallSite> getProductByIdCallSites() {
        return List.of(
                InventoryCallSite.of("/products/{id}", "GET", "Client Service A", "clients/client-a/legacy_calls.sh"),
                InventoryCallSite.of("/products/{id}", "GET", "Client Service B", "clients/client-b/legacy_calls.mjs"),
                InventoryCallSite.of("/products/{id}", "GET", "Client Service C", "clients/client-c/legacy_calls.py"));
    }

    public static List<InventoryCallSite> getOrderByIdCallSites() {
        return List.of(
                InventoryCallSite.of("/orders/{id}", "GET", "Client Service A", "clients/client-a/legacy_calls.sh"),
                InventoryCallSite.of("/orders/{id}", "GET", "Client Service B", "clients/client-b/legacy_calls.mjs"));
    }

    public static List<InventoryCallSite> getOrdersByCustomerIdCallSites() {
        return List.of(InventoryCallSite.of(
                "/orders?customer_id={customer_id}",
                "GET",
                "Client Service A",
                "clients/client-a/legacy_calls.sh"));
    }

    public static List<InventoryCallSite> postOrderCallSites() {
        return List.of(InventoryCallSite.of(
                "/orders", "POST", "Client Service A", "clients/client-a/legacy_calls.sh"));
    }

    public static List<InventoryCallSite> getCustomersCallSites() {
        return List.of(InventoryCallSite.of(
                "/customers", "GET", "Client Service B", "clients/client-b/legacy_calls.mjs"));
    }

    public static List<InventoryCallSite> getCustomerByIdCallSites() {
        return List.of(InventoryCallSite.of(
                "/customers/{id}", "GET", "Client Service B", "clients/client-b/legacy_calls.mjs"));
    }

    public static List<InventoryCallSite> getOrdersCallSites() {
        return List.of(InventoryCallSite.of(
                "/orders", "GET", "Client Service C", "clients/client-c/legacy_calls.py"));
    }

    public static List<InventoryCallSite> getOrderStatusCallSites() {
        return List.of(InventoryCallSite.of(
                "/orders/{id}/status", "GET", "Client Service C", "clients/client-c/legacy_calls.py"));
    }

    public static List<InventoryCallSite> getCustomerOrdersCallSites() {
        return List.of(InventoryCallSite.of(
                "/customers/{id}/orders",
                "GET",
                "Client Service C",
                "clients/client-c/legacy_calls.py"));
    }
}
