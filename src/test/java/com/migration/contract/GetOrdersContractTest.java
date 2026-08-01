package com.migration.contract;

import com.migration.contract.support.ContractTestBase;
import com.migration.contract.support.InventoryCallSite;
import com.migration.contract.support.InventoryCatalog;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GetOrdersContractTest extends ContractTestBase {

    @Test
    void matchesLegacyApi() throws JSONException {
        assertShimMatchesLegacyGet("/orders");
    }

    @ParameterizedTest(name = "inventory call site covered: {0}")
    @MethodSource("inventoryCallSites")
    void coversInventoryCallSite(InventoryCallSite callSite) throws JSONException {
        assertShimMatchesLegacyGet("/orders");
    }

    static java.util.stream.Stream<InventoryCallSite> inventoryCallSites() {
        return InventoryCatalog.getOrdersCallSites().stream();
    }
}
