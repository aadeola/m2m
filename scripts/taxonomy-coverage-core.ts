import { existsSync, readFileSync } from "node:fs";
import { join, resolve } from "node:path";
import {
  type CoverageReport,
  type UncoveredCallSite,
  runCoverageCheck,
  validateRepoLayout,
} from "./coverage-check-core.ts";

/** Mirrors com.migration.support.TestTaxonomy.Category */
export type TaxonomyCategory = "CONTRACT" | "ROUTING" | "TRANSFORM" | "MIGRATION" | "SMOKE";

export type BucketStatus = "implemented" | "missing";

/** One expected test bucket in the taxonomy matrix */
export interface TaxonomyBucket {
  bucket_id: string;
  category: TaxonomyCategory;
  test_class: string;
  package_path: string;
  scenario: string;
  primary_question: string;
  guidance: string;
}

export interface ScoredBucket extends TaxonomyBucket {
  status: BucketStatus;
  relative_file_path: string;
}

export interface CategorySummary {
  total: number;
  implemented: number;
  percent: number;
}

export interface TaxonomyCoverageReport {
  taxonomy_coverage_percent: number;
  total_buckets: number;
  implemented_buckets: ScoredBucket[];
  missing_buckets: ScoredBucket[];
  by_category: Record<TaxonomyCategory, CategorySummary>;
  contract_call_sites: CoverageReport;
}

const TEST_JAVA_ROOT = "src/test/java/com/migration";

function contractBucket(
  bucketId: string,
  testClass: string,
  scenario: string,
  guidance: string,
): TaxonomyBucket {
  return {
    bucket_id: bucketId,
    category: "CONTRACT",
    test_class: testClass,
    package_path: "contract",
    scenario,
    primary_question: "Does the shim preserve the legacy client contract?",
    guidance,
  };
}

function routingBucket(
  bucketId: string,
  testClass: string,
  scenario: string,
  guidance: string,
): TaxonomyBucket {
  return {
    bucket_id: bucketId,
    category: "ROUTING",
    test_class: testClass,
    package_path: "routing",
    scenario,
    primary_question: "Does the shim route reads/writes to the correct authoritative store?",
    guidance,
  };
}

function transformBucket(
  bucketId: string,
  testClass: string,
  scenario: string,
  guidance: string,
): TaxonomyBucket {
  return {
    bucket_id: bucketId,
    category: "TRANSFORM",
    test_class: testClass,
    package_path: "transform",
    scenario,
    primary_question: "Are relational, document, and DTO mappings deterministic and correct?",
    guidance,
  };
}

function migrationBucket(
  bucketId: string,
  testClass: string,
  scenario: string,
  guidance: string,
): TaxonomyBucket {
  return {
    bucket_id: bucketId,
    category: "MIGRATION",
    test_class: testClass,
    package_path: "migration",
    scenario,
    primary_question: "Does the backfill job migrate data and update checkpoints correctly?",
    guidance,
  };
}

/**
 * Canonical taxonomy matrix with scenario-level buckets.
 * Implementation status is derived from test-class file presence on disk.
 * Keep in sync with TestTaxonomy.java existingSuiteMapping().
 */
export const TAXONOMY_BUCKETS: TaxonomyBucket[] = [
  contractBucket(
    "contract.get-product-by-id",
    "GetProductByIdContractTest",
    "GET /products/{id}",
    "Extend ContractTestBase; assert shim JSON matches legacy oracle for migrated and unmigrated product.",
  ),
  contractBucket(
    "contract.get-order-by-id",
    "GetOrderByIdContractTest",
    "GET /orders/{id}",
    "Extend ContractTestBase; cover migrated and unmigrated order reads.",
  ),
  contractBucket(
    "contract.get-orders-by-customer-id",
    "GetOrdersByCustomerIdContractTest",
    "GET /orders?customer_id={customer_id}",
    "Extend ContractTestBase; list orders filtered by customer_id.",
  ),
  contractBucket(
    "contract.post-order",
    "PostOrderContractTest",
    "POST /orders",
    "Extend ContractTestBase; verify write path preserves legacy response shape.",
  ),
  contractBucket(
    "contract.get-customers",
    "GetCustomersContractTest",
    "GET /customers",
    "Extend ContractTestBase; list customers endpoint parity.",
  ),
  contractBucket(
    "contract.get-customer-by-id",
    "GetCustomerByIdContractTest",
    "GET /customers/{id}",
    "Extend ContractTestBase; single customer read parity.",
  ),
  contractBucket(
    "contract.get-orders",
    "GetOrdersContractTest",
    "GET /orders",
    "Extend ContractTestBase; list all orders endpoint parity.",
  ),
  contractBucket(
    "contract.get-order-status",
    "GetOrderStatusContractTest",
    "GET /orders/{id}/status",
    "Extend ContractTestBase; order status sub-resource parity.",
  ),
  contractBucket(
    "contract.get-customer-orders",
    "GetCustomerOrdersContractTest",
    "GET /customers/{id}/orders",
    "Extend ContractTestBase; nested customer orders route parity.",
  ),
  contractBucket(
    "contract.break-detection",
    "ContractBreakDetectionTest",
    "Regression guard",
    "Detect accidental contract shape drift across endpoints.",
  ),
  routingBucket(
    "routing.datasource-resolver.numeric-id",
    "DataSourceResolverTest",
    "Numeric Postgres PK resolution",
    "Unit test with Mockito; numeric IDs route to Postgres repositories.",
  ),
  routingBucket(
    "routing.datasource-resolver.object-id",
    "DataSourceResolverTest",
    "Mongo ObjectId resolution",
    "Unit test; 24-char hex ObjectIds route to Mongo repositories.",
  ),
  routingBucket(
    "routing.datasource-resolver.invalid-id",
    "DataSourceResolverTest",
    "Invalid ID handling",
    "Unit test; malformed IDs throw or reject consistently.",
  ),
  routingBucket(
    "routing.order-service.unmigrated-read",
    "OrderServiceRoutingTest",
    "Unmigrated order read from Postgres",
    "Service-level test; migrated_at null reads Postgres.",
  ),
  routingBucket(
    "routing.order-service.migrated-read",
    "OrderServiceRoutingTest",
    "Migrated order read from Mongo",
    "Service-level test; migrated_at set reads Mongo.",
  ),
  routingBucket(
    "routing.order-service.write-routing",
    "OrderServiceRoutingTest",
    "Write path routing",
    "Verify writes land in the correct store based on migration state.",
  ),
  transformBucket(
    "transform.customer.to-document",
    "CustomerTransformerTest",
    "Customer toDocument",
    "Pure unit test; JPA entity to Mongo document mapping.",
  ),
  transformBucket(
    "transform.customer.to-response",
    "CustomerTransformerTest",
    "Customer toResponse",
    "Pure unit test; document/entity to legacy DTO shape.",
  ),
  transformBucket(
    "transform.product.to-document",
    "ProductTransformerTest",
    "Product toDocument",
    "Pure unit test; product entity to document mapping.",
  ),
  transformBucket(
    "transform.product.to-response",
    "ProductTransformerTest",
    "Product toResponse",
    "Pure unit test; product to legacy response DTO.",
  ),
  transformBucket(
    "transform.order.to-document",
    "OrderTransformerTest",
    "Order toDocument with embedded line items",
    "Pure unit test; order + line items embedded document shape.",
  ),
  transformBucket(
    "transform.order.to-response",
    "OrderTransformerTest",
    "Order toResponse",
    "Pure unit test; document to legacy order JSON fields.",
  ),
  transformBucket(
    "transform.order.parse-id",
    "OrderTransformerTest",
    "parseId edge cases",
    "Cover numeric vs ObjectId ID parsing used by routing.",
  ),
  migrationBucket(
    "migration.backfill-service.customers",
    "BackfillServiceTest",
    "Migrate customers batch",
    "Unit/integration test; customers copied to Mongo with migrated_at stamped.",
  ),
  migrationBucket(
    "migration.backfill-service.products",
    "BackfillServiceTest",
    "Migrate products batch",
    "Verify product rows migrate and checkpoint advances.",
  ),
  migrationBucket(
    "migration.backfill-service.orders",
    "BackfillServiceTest",
    "Migrate orders batch",
    "Verify orders + line items embed correctly during backfill.",
  ),
  migrationBucket(
    "migration.backfill-service.checkpoint",
    "BackfillServiceTest",
    "Checkpoint persistence",
    "Assert last-processed PK checkpoint updates between batches.",
  ),
  migrationBucket(
    "migration.backfill-service.migrated-at",
    "BackfillServiceTest",
    "migrated_at stamping",
    "Assert Postgres migrated_at is set after successful migration.",
  ),
  migrationBucket(
    "migration.backfill-job.gating",
    "BackfillJobTest",
    "--backfill flag gating",
    "BackfillJob runs only when --backfill argument is present.",
  ),
  migrationBucket(
    "migration.backfill-job.resumability",
    "BackfillJobTest",
    "Resumable batch processing",
    "Job resumes from checkpoint without re-processing completed PKs.",
  ),
  {
    bucket_id: "smoke.application-context",
    category: "SMOKE",
    test_class: "MigrationShimApplicationTests",
    package_path: "",
    scenario: "Spring context startup",
    primary_question: "Does the Spring application context start?",
    guidance: "@SpringBootTest smoke test; context loads without errors.",
  },
];

export function relativeBucketFilePath(bucket: TaxonomyBucket): string {
  if (bucket.category === "SMOKE") {
    return join(TEST_JAVA_ROOT, `${bucket.test_class}.java`);
  }
  return join(TEST_JAVA_ROOT, bucket.package_path, `${bucket.test_class}.java`);
}

function testClassFileExists(repoRoot: string, testClass: string, packagePath: string): boolean {
  if (packagePath === "") {
    return existsSync(join(repoRoot, TEST_JAVA_ROOT, `${testClass}.java`));
  }
  return existsSync(join(repoRoot, TEST_JAVA_ROOT, packagePath, `${testClass}.java`));
}

function emptyCategorySummaries(): Record<TaxonomyCategory, CategorySummary> {
  return {
    CONTRACT: { total: 0, implemented: 0, percent: 0 },
    ROUTING: { total: 0, implemented: 0, percent: 0 },
    TRANSFORM: { total: 0, implemented: 0, percent: 0 },
    MIGRATION: { total: 0, implemented: 0, percent: 0 },
    SMOKE: { total: 0, implemented: 0, percent: 0 },
  };
}

function categoryPercent(implemented: number, total: number): number {
  if (total === 0) {
    return 100;
  }
  return Math.round((implemented / total) * 100);
}

function scoreBucket(repoRoot: string, bucket: TaxonomyBucket): ScoredBucket {
  const implemented = testClassFileExists(repoRoot, bucket.test_class, bucket.package_path);
  return {
    ...bucket,
    status: implemented ? "implemented" : "missing",
    relative_file_path: relativeBucketFilePath(bucket),
  };
}

/** Unique test classes referenced by missing buckets (for targeted mvn test). */
export function uniqueMissingTestClasses(report: TaxonomyCoverageReport): string[] {
  const classes = new Set<string>();
  for (const bucket of report.missing_buckets) {
    classes.add(bucket.test_class);
  }
  for (const site of report.contract_call_sites.uncovered_call_sites) {
    const suggestion = report.contract_call_sites.stub_test_suggestions.find(
      (s) => s.method === site.method && s.endpoint === site.endpoint,
    );
    if (suggestion) {
      classes.add(suggestion.suggested_test_class);
    }
  }
  return [...classes];
}

export function runTaxonomyCoverageCheck(repoRoot: string): TaxonomyCoverageReport {
  const resolvedRoot = resolve(repoRoot);
  validateRepoLayout(resolvedRoot);

  const implemented_buckets: ScoredBucket[] = [];
  const missing_buckets: ScoredBucket[] = [];
  const by_category = emptyCategorySummaries();

  for (const bucket of TAXONOMY_BUCKETS) {
    by_category[bucket.category].total += 1;
    const scored = scoreBucket(resolvedRoot, bucket);
    if (scored.status === "implemented") {
      implemented_buckets.push(scored);
      by_category[bucket.category].implemented += 1;
    } else {
      missing_buckets.push(scored);
    }
  }

  for (const category of Object.keys(by_category) as TaxonomyCategory[]) {
    const summary = by_category[category];
    summary.percent = categoryPercent(summary.implemented, summary.total);
  }

  const taxonomy_coverage_percent = categoryPercent(
    implemented_buckets.length,
    TAXONOMY_BUCKETS.length,
  );

  const contract_call_sites = runCoverageCheck(resolvedRoot);

  return {
    taxonomy_coverage_percent,
    total_buckets: TAXONOMY_BUCKETS.length,
    implemented_buckets,
    missing_buckets,
    by_category,
    contract_call_sites,
  };
}

export function isTaxonomyCoverageComplete(report: TaxonomyCoverageReport): boolean {
  return (
    report.missing_buckets.length === 0 &&
    report.contract_call_sites.uncovered_call_sites.length === 0
  );
}

export function formatTaxonomyHumanReport(report: TaxonomyCoverageReport): string {
  const lines: string[] = [];
  lines.push("Taxonomy Coverage Report");
  lines.push("========================");
  lines.push(
    `Taxonomy build-out: ${report.implemented_buckets.length}/${report.total_buckets} buckets (${report.taxonomy_coverage_percent}%)`,
  );
  lines.push("");

  lines.push("By category:");
  for (const category of ["CONTRACT", "ROUTING", "TRANSFORM", "MIGRATION", "SMOKE"] as const) {
    const summary = report.by_category[category];
    lines.push(`  - ${category}: ${summary.implemented}/${summary.total} (${summary.percent}%)`);
  }
  lines.push("");

  if (report.missing_buckets.length > 0) {
    lines.push("Missing taxonomy buckets:");
    const seen = new Set<string>();
    for (const bucket of report.missing_buckets) {
      const key = `${bucket.test_class}:${bucket.scenario}`;
      if (seen.has(key)) {
        continue;
      }
      seen.add(key);
      lines.push(`  - [${bucket.category}] ${bucket.test_class} — ${bucket.scenario}`);
    }
    lines.push("");
  } else {
    lines.push("All taxonomy buckets have test classes on disk.");
    lines.push("");
  }

  const contract = report.contract_call_sites;
  lines.push("Contract call-site coverage (inventory.json):");
  lines.push(`  Total call sites: ${contract.total_call_sites}`);
  lines.push(`  Covered: ${contract.covered_call_sites.length}`);
  lines.push(`  Uncovered: ${contract.uncovered_call_sites.length}`);

  if (contract.uncovered_call_sites.length > 0) {
    lines.push("");
    lines.push("Uncovered call sites:");
    for (const site of contract.uncovered_call_sites) {
      lines.push(
        `  - ${site.method} ${site.endpoint} (${site.owning_service}, ${site.source_file})`,
      );
      lines.push(`    reason: ${site.reason}`);
    }
  }

  lines.push("");
  if (isTaxonomyCoverageComplete(report)) {
    lines.push("Pass: taxonomy matrix and inventory call sites are fully covered.");
  } else {
    lines.push("Fail: gaps remain in taxonomy buckets and/or inventory call-site coverage.");
  }

  return lines.join("\n");
}

/** Verify TestTaxonomy.java exists (source of truth for bucket definitions). */
export function validateTaxonomySource(repoRoot: string): void {
  const taxonomyPath = join(
    repoRoot,
    "src/test/java/com/migration/support/TestTaxonomy.java",
  );
  if (!existsSync(taxonomyPath)) {
    throw new Error(`TestTaxonomy.java not found at ${taxonomyPath}`);
  }
  readFileSync(taxonomyPath, "utf8");
}

/** Collapse missing scenario buckets to one entry per missing test class for remediation. */
export function collapseMissingBucketsByTestClass(
  missing: ScoredBucket[],
): ScoredBucket[] {
  const byClass = new Map<string, ScoredBucket>();
  for (const bucket of missing) {
    if (!byClass.has(bucket.test_class)) {
      byClass.set(bucket.test_class, bucket);
    }
  }
  return [...byClass.values()];
}

export type { UncoveredCallSite };
