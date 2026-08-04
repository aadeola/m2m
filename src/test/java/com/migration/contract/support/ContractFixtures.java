package com.migration.contract.support;

/**
 * Stable fixture identifiers shared by the contract-test harness and legacy stub.
 */
public final class ContractFixtures {

    public static final int UNMIGRATED_PRODUCT_ID = 1;
    public static final int MIGRATED_PRODUCT_ID = 2;
    public static final String OBJECT_ID_PRODUCT = "507f191e810c19729de860ea";

    public static final int UNMIGRATED_CUSTOMER_ID = 1;
    public static final int MIGRATED_CUSTOMER_ID = 2;
    public static final String OBJECT_ID_CUSTOMER = "507f191e810c19729de860eb";

    public static final int UNMIGRATED_ORDER_ID = 1;
    public static final int MIGRATED_ORDER_ID = 2;
    public static final String OBJECT_ID_ORDER = "507f191e810c19729de860ec";

    private ContractFixtures() {
    }

    public static String productId(RoutingScenario scenario) {
        return switch (scenario) {
            case UNMIGRATED -> String.valueOf(UNMIGRATED_PRODUCT_ID);
            case MIGRATED -> String.valueOf(MIGRATED_PRODUCT_ID);
            case NEW -> OBJECT_ID_PRODUCT;
        };
    }

    public static String customerId(RoutingScenario scenario) {
        return switch (scenario) {
            case UNMIGRATED -> String.valueOf(UNMIGRATED_CUSTOMER_ID);
            case MIGRATED -> String.valueOf(MIGRATED_CUSTOMER_ID);
            case NEW -> OBJECT_ID_CUSTOMER;
        };
    }

    public static String orderId(RoutingScenario scenario) {
        return switch (scenario) {
            case UNMIGRATED -> String.valueOf(UNMIGRATED_ORDER_ID);
            case MIGRATED -> String.valueOf(MIGRATED_ORDER_ID);
            case NEW -> OBJECT_ID_ORDER;
        };
    }
}
