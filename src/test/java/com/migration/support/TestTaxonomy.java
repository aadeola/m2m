package com.migration.support;

import java.util.List;
import java.util.Map;

/**
 * Registry of the test taxonomy: contract, routing, transform, migration, and smoke.
 * Each category answers one primary question; overlap is intentional only at integration boundaries.
 */
public final class TestTaxonomy {

    public enum Category {
        /** Legacy API JSON parity via shim vs legacy oracle (integration). */
        CONTRACT,
        /** DataSourceResolver and service routing/merge decisions (unit + focused integration). */
        ROUTING,
        /** Deterministic entity/document/DTO mapping (unit, no database). */
        TRANSFORM,
        /** Backfill workflow, checkpoints, migrated_at stamping (integration). */
        MIGRATION,
        /** Application startup smoke check (smoke). */
        SMOKE
    }

    private TestTaxonomy() {
    }

    /** Maps each taxonomy category to its test package and naming convention. */
    public static Map<Category, PackageConvention> conventions() {
        return Map.of(
                Category.CONTRACT,
                new PackageConvention(
                        "com.migration.contract",
                        "*ContractTest",
                        "Does the shim preserve the legacy client contract?"),
                Category.ROUTING,
                new PackageConvention(
                        "com.migration.routing",
                        "*Test",
                        "Does the shim route reads/writes to the correct authoritative store?"),
                Category.TRANSFORM,
                new PackageConvention(
                        "com.migration.transform",
                        "*TransformerTest",
                        "Are relational, document, and DTO mappings deterministic and correct?"),
                Category.MIGRATION,
                new PackageConvention(
                        "com.migration.migration",
                        "*Test / *IntegrationTest",
                        "Does the backfill job migrate data and update checkpoints correctly?"),
                Category.SMOKE,
                new PackageConvention(
                        "com.migration",
                        "MigrationShimApplicationTests",
                        "Does the Spring application context start?"));
    }

    /**
     * Existing contract tests remain the external-behavior layer; routing/transform/migration
     * tests complement them with direct coverage of internal concerns.
     */
    public static Map<Category, List<String>> existingSuiteMapping() {
        return Map.of(
                Category.CONTRACT,
                List.of(
                        "GetProductByIdContractTest",
                        "GetOrderByIdContractTest",
                        "GetOrdersByCustomerIdContractTest",
                        "PostOrderContractTest",
                        "GetCustomersContractTest",
                        "GetCustomerByIdContractTest",
                        "GetOrdersContractTest",
                        "GetOrderStatusContractTest",
                        "GetCustomerOrdersContractTest",
                        "ContractBreakDetectionTest"),
                Category.ROUTING,
                List.of("DataSourceResolverTest", "OrderServiceRoutingTest"),
                Category.TRANSFORM,
                List.of(
                        "CustomerTransformerTest",
                        "ProductTransformerTest",
                        "OrderTransformerTest"),
                Category.MIGRATION,
                List.of("BackfillServiceTest", "BackfillJobTest"),
                Category.SMOKE,
                List.of("MigrationShimApplicationTests"));
    }

    public record PackageConvention(String packageName, String classNamePattern, String primaryQuestion) {}
}
