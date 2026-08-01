package com.migration.contract;

import com.migration.contract.support.ContractFixtures;
import com.migration.contract.support.ContractTestBase;
import com.migration.contract.support.InventoryCallSite;
import com.migration.contract.support.InventoryCatalog;
import com.migration.contract.support.RoutingScenario;
import java.util.stream.Stream;
import org.json.JSONException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class GetOrderStatusContractTest extends ContractTestBase {

    @ParameterizedTest(name = "{0} routing")
    @EnumSource(RoutingScenario.class)
    void matchesLegacyApi(RoutingScenario scenario) throws JSONException {
        String orderId = ContractFixtures.orderId(scenario);
        assertShimMatchesLegacyGet("/orders/" + orderId + "/status");
    }

    @ParameterizedTest(name = "inventory call site covered: {0}")
    @MethodSource("inventoryCallSites")
    void coversInventoryCallSite(InventoryCallSite callSite) throws JSONException {
        assertShimMatchesLegacyGet("/orders/" + ContractFixtures.UNMIGRATED_ORDER_ID + "/status");
    }

    static Stream<InventoryCallSite> inventoryCallSites() {
        return InventoryCatalog.getOrderStatusCallSites().stream();
    }
}
