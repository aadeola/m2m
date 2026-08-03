package com.migration.contract;

import com.migration.contract.support.ContractFixtures;
import com.migration.contract.support.ContractTestBase;
import com.migration.contract.support.InventoryCallSite;
import com.migration.contract.support.InventoryCatalog;
import org.json.JSONException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GetOrdersByCustomerIdContractTest extends ContractTestBase {

    @ParameterizedTest(name = "customer {0} via {1}")
    @MethodSource("customerAndCallSites")
    void matchesLegacyApi(int customerId, InventoryCallSite callSite) throws JSONException {
        assertShimMatchesLegacyGetWithQuery("/orders", "customer_id", String.valueOf(customerId));
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> customerAndCallSites() {
        return InventoryCatalog.getOrdersByCustomerIdCallSites().stream()
                .flatMap(callSite -> java.util.stream.Stream.of(
                        ContractFixtures.UNMIGRATED_CUSTOMER_ID,
                        ContractFixtures.MIGRATED_CUSTOMER_ID)
                        .map(customerId -> org.junit.jupiter.params.provider.Arguments.of(customerId, callSite)));
    }
}
