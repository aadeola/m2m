package com.migration.contract;

import com.migration.contract.support.ContractAssertions;
import com.migration.contract.support.ContractFixtureSetup;
import com.migration.contract.support.ContractTestBase;
import com.migration.contract.support.InventoryCallSite;
import com.migration.contract.support.InventoryCatalog;
import org.json.JSONException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

class PostOrderContractTest extends ContractTestBase {

    @Autowired
    private ContractFixtureSetup contractFixtureSetup;

    @ParameterizedTest(name = "inventory call site covered: {0}")
    @MethodSource("inventoryCallSites")
    void matchesLegacyApi(InventoryCallSite callSite) throws JSONException {
        var request = contractFixtureSetup.sampleCreateOrderRequest();
        String legacyTemplate = legacyApiStub.postLegacy("/orders", request);
        String shimResponse = callShimPost("/orders", request);
        ContractAssertions.assertCreateOrderResponse(legacyTemplate, shimResponse);
    }

    static java.util.stream.Stream<InventoryCallSite> inventoryCallSites() {
        return InventoryCatalog.postOrderCallSites().stream();
    }
}
