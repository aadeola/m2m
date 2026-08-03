package com.migration.contract.support;

/**
 * Routing states exercised by contract tests.
 *
 * Fixture matrix (IDs are stable across the suite):
 * <ul>
 *   <li>Product: unmigrated=1, migrated=2, objectId={@link ContractFixtures#OBJECT_ID_PRODUCT}</li>
 *   <li>Customer: unmigrated=1, migrated=2, objectId={@link ContractFixtures#OBJECT_ID_CUSTOMER}</li>
 *   <li>Order: unmigrated=1, migrated=2, objectId={@link ContractFixtures#OBJECT_ID_ORDER}</li>
 * </ul>
 */
public enum RoutingScenario {
    UNMIGRATED,
    MIGRATED,
    OBJECT_ID
}
