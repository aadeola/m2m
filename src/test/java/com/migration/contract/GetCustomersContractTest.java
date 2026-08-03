package com.migration.contract;

import com.migration.contract.support.ContractTestBase;
import com.migration.contract.support.InventoryCatalog;
import java.util.stream.Stream;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GetCustomersContractTest extends ContractTestBase {

    @Test
    void matchesLegacyApi() throws JSONException {
        assertShimMatchesLegacyGet("/customers");
    }

    @ParameterizedTest(name = "inventory call site covered: {0}")
    @MethodSource("inventoryCallSites")
    void coversInventoryCallSite(String owningService) throws JSONException {
        assertShimMatchesLegacyGet("/customers");
    }

    static Stream<String> inventoryCallSites() {
        return InventoryCatalog.getCustomersCallSites().stream().map(callSite -> callSite.owningService());
    }
}
