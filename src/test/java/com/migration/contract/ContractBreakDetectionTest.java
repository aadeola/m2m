package com.migration.contract;

import com.migration.contract.support.ContractAssertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves the contract suite fails when legacy and shim responses diverge.
 */
class ContractBreakDetectionTest {

    @Test
    void detectsFieldMismatch() {
        assertThrows(AssertionError.class, () -> ContractAssertions.assertJsonEqual(
                "{\"product_id\":1,\"name\":\"Wireless Mouse\",\"sku\":\"WM-001\",\"price\":29.99}",
                "{\"product_id\":1,\"name\":\"Broken Mouse\",\"sku\":\"WM-001\",\"price\":29.99}"));
    }
}
