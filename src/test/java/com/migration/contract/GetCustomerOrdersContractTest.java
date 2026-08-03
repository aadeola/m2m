package com.migration.contract;

import com.migration.contract.support.ContractFixtures;
import com.migration.contract.support.ContractTestBase;
import com.migration.contract.support.InventoryCallSite;
import com.migration.contract.support.InventoryCatalog;
import com.migration.contract.support.RoutingScenario;
import org.json.JSONException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class GetCustomerOrdersContractTest extends ContractTestBase {

    @ParameterizedTest(name = "{0} routing")
    @EnumSource(RoutingScenario.class)
    void matchesLegacyApi(RoutingScenario scenario) throws JSONException {
        String customerId = ContractFixtures.customerId(scenario);
        assertShimMatchesLegacyGet("/customers/" + customerId + "/orders");
    }

    @ParameterizedTest(name = "inventory call site covered: {0}")
    @MethodSource("inventoryCallSites")
    void coversInventoryCallSite(InventoryCallSite callSite) throws JSONException {
        assertShimMatchesLegacyGet("/customers/" + ContractFixtures.UNMIGRATED_CUSTOMER_ID + "/orders");
    }

    static java.util.stream.Stream<InventoryCallSite> inventoryCallSites() {
        return InventoryCatalog.getCustomerOrdersCallSites().stream();
    }
}
