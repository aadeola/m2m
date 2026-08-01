package com.migration.contract;

import com.migration.contract.support.ContractFixtures;
import com.migration.contract.support.ContractTestBase;
import com.migration.contract.support.InventoryCallSite;
import com.migration.contract.support.InventoryCatalog;
import com.migration.contract.support.RoutingScenario;
import java.util.stream.Stream;
import org.json.JSONException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class GetProductByIdContractTest extends ContractTestBase {

    @ParameterizedTest(name = "{0} routing via {1}")
    @MethodSource("routingAndCallSites")
    void matchesLegacyApi(RoutingScenario scenario, InventoryCallSite callSite) throws JSONException {
        String productId = ContractFixtures.productId(scenario);
        assertShimMatchesLegacyGet("/products/" + productId);
    }

    static Stream<Arguments> routingAndCallSites() {
        return InventoryCatalog.getProductByIdCallSites().stream()
                .flatMap(callSite -> Stream.of(RoutingScenario.values()).map(scenario -> Arguments.of(scenario, callSite)));
    }

    @ParameterizedTest(name = "inventory call site covered: {0}")
    @MethodSource("inventoryCallSites")
    void coversInventoryCallSite(InventoryCallSite callSite) throws JSONException {
        assertShimMatchesLegacyGet("/products/" + ContractFixtures.UNMIGRATED_PRODUCT_ID);
    }

    static Stream<InventoryCallSite> inventoryCallSites() {
        return InventoryCatalog.getProductByIdCallSites().stream();
    }
}
