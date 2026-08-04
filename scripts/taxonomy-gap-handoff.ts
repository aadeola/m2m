import {
  collapseMissingBucketsByTestClass,
  type ScoredBucket,
  type TaxonomyCategory,
  type TaxonomyCoverageReport,
} from "./taxonomy-coverage-core.ts";

/** One remediation item handed to the SDK agent */
export interface TaxonomyGapItem {
  bucket_id: string;
  category: TaxonomyCategory;
  test_class: string;
  package_path: string;
  relative_file_path: string;
  scenario: string;
  guidance: string;
  scenarios: string[];
}

export interface ContractCallSiteGap {
  method: string;
  endpoint: string;
  suggested_test_class: string;
  owning_service: string;
  source_file: string;
  reason: string;
}

export interface TaxonomyGapHandoff {
  before_report: Pick<
    TaxonomyCoverageReport,
    "taxonomy_coverage_percent" | "total_buckets" | "by_category"
  >;
  taxonomy_gaps: TaxonomyGapItem[];
  contract_gaps: ContractCallSiteGap[];
  gaps_by_category: Record<TaxonomyCategory, TaxonomyGapItem[]>;
  total_gap_count: number;
}

const CATEGORY_GENERATION_RULES: Record<TaxonomyCategory, string> = {
  CONTRACT:
    "Generate *ContractTest in com.migration.contract extending ContractTestBase. " +
    "Compare shim HTTP responses to LegacyApiStub oracle. Cover migrated and unmigrated records. " +
    "Do NOT assert Mongo document shape — only legacy JSON parity.",
  ROUTING:
    "Generate focused unit tests in com.migration.routing using Mockito. " +
    "Test DataSourceResolver ID parsing and OrderService store selection without starting databases.",
  TRANSFORM:
    "Generate *TransformerTest in com.migration.transform as pure JUnit unit tests. " +
    "No Spring context or database. Cover toDocument, toResponse, and ID parsing edge cases.",
  MIGRATION:
    "Generate tests in com.migration.migration for BackfillService/BackfillJob. " +
    "Prefer unit tests with mocks; verify checkpointing, migrated_at stamping, and --backfill gating.",
  SMOKE:
    "Generate MigrationShimApplicationTests as @SpringBootTest context-load smoke test.",
};

function groupScenariosByTestClass(buckets: ScoredBucket[]): TaxonomyGapItem[] {
  const grouped = new Map<string, TaxonomyGapItem>();

  for (const bucket of buckets) {
    const existing = grouped.get(bucket.test_class);
    if (existing) {
      existing.scenarios.push(bucket.scenario);
      continue;
    }

    grouped.set(bucket.test_class, {
      bucket_id: bucket.bucket_id,
      category: bucket.category,
      test_class: bucket.test_class,
      package_path: bucket.package_path,
      relative_file_path: bucket.relative_file_path,
      scenario: bucket.scenario,
      guidance: bucket.guidance,
      scenarios: [bucket.scenario],
    });
  }

  return [...grouped.values()];
}

function buildContractGaps(report: TaxonomyCoverageReport): ContractCallSiteGap[] {
  const gaps: ContractCallSiteGap[] = [];

  for (const site of report.contract_call_sites.uncovered_call_sites) {
    const suggestion = report.contract_call_sites.stub_test_suggestions.find(
      (s) => s.method === site.method && s.endpoint === site.endpoint,
    );
    gaps.push({
      method: site.method,
      endpoint: site.endpoint,
      suggested_test_class: suggestion?.suggested_test_class ?? "UnknownContractTest",
      owning_service: site.owning_service,
      source_file: site.source_file,
      reason: site.reason,
    });
  }

  return gaps;
}

function emptyGapsByCategory(): Record<TaxonomyCategory, TaxonomyGapItem[]> {
  return {
    CONTRACT: [],
    ROUTING: [],
    TRANSFORM: [],
    MIGRATION: [],
    SMOKE: [],
  };
}

export function buildGapHandoff(report: TaxonomyCoverageReport): TaxonomyGapHandoff {
  const collapsed = collapseMissingBucketsByTestClass(report.missing_buckets);
  const taxonomy_gaps = groupScenariosByTestClass(collapsed);
  const contract_gaps = buildContractGaps(report);

  const gaps_by_category = emptyGapsByCategory();
  for (const gap of taxonomy_gaps) {
    gaps_by_category[gap.category].push(gap);
  }

  return {
    before_report: {
      taxonomy_coverage_percent: report.taxonomy_coverage_percent,
      total_buckets: report.total_buckets,
      by_category: report.by_category,
    },
    taxonomy_gaps,
    contract_gaps,
    gaps_by_category,
    total_gap_count: taxonomy_gaps.length + contract_gaps.length,
  };
}

export function buildRemediationPrompt(handoff: TaxonomyGapHandoff, branchName: string): string {
  const sections: string[] = [];

  sections.push(
    "You are remediating test taxonomy gaps for the m2m migration shim repository.",
  );
  sections.push("");
  sections.push("## Constraints");
  sections.push("- Do NOT merge branches or push to main.");
  sections.push("- Do NOT use MongoDB MCP.");
  sections.push("- Follow AGENTS.md and TestTaxonomy.java package conventions.");
  sections.push(`- Work on branch: ${branchName}`);
  sections.push("- Add only the missing tests listed below; do not refactor unrelated code.");
  sections.push("");

  sections.push("## Current coverage (before remediation)");
  sections.push(JSON.stringify(handoff.before_report, null, 2));
  sections.push("");

  sections.push("## Gap handoff (structured)");
  sections.push("```json");
  sections.push(JSON.stringify(handoff, null, 2));
  sections.push("```");
  sections.push("");

  sections.push("## Generation rules by category");
  for (const category of ["CONTRACT", "ROUTING", "TRANSFORM", "MIGRATION", "SMOKE"] as const) {
    const gaps = handoff.gaps_by_category[category];
    if (gaps.length === 0 && category !== "CONTRACT") {
      continue;
    }
    sections.push(`### ${category}`);
    sections.push(CATEGORY_GENERATION_RULES[category]);
    if (gaps.length > 0) {
      sections.push("Missing test classes:");
      for (const gap of gaps) {
        sections.push(
          `- ${gap.test_class} (${gap.relative_file_path}): scenarios: ${gap.scenarios.join("; ")}`,
        );
      }
    }
    sections.push("");
  }

  if (handoff.contract_gaps.length > 0) {
    sections.push("## Uncovered inventory call sites");
    for (const gap of handoff.contract_gaps) {
      sections.push(
        `- ${gap.method} ${gap.endpoint} -> ${gap.suggested_test_class} (${gap.reason})`,
      );
    }
    sections.push("");
  }

  sections.push("## Deliverables");
  sections.push("1. Create each missing test class file with meaningful assertions.");
  sections.push("2. Update TestTaxonomy.java existingSuiteMapping if you add new test classes.");
  sections.push("3. End with a JSON summary block:");
  sections.push("```json");
  sections.push(
    JSON.stringify(
      {
        tests_added: ["ExampleTest"],
        categories: ["ROUTING"],
        notes: "Brief rationale per test group",
      },
      null,
      2,
    ),
  );
  sections.push("```");

  return sections.join("\n");
}

export function formatGapHandoffSummary(handoff: TaxonomyGapHandoff): string {
  const lines: string[] = [];
  lines.push(`Gap handoff: ${handoff.total_gap_count} remediation item(s)`);
  lines.push(`  Taxonomy test classes missing: ${handoff.taxonomy_gaps.length}`);
  lines.push(`  Uncovered call sites: ${handoff.contract_gaps.length}`);
  lines.push("");
  for (const category of ["CONTRACT", "ROUTING", "TRANSFORM", "MIGRATION", "SMOKE"] as const) {
    const gaps = handoff.gaps_by_category[category];
    if (gaps.length === 0) {
      continue;
    }
    lines.push(`${category}:`);
    for (const gap of gaps) {
      lines.push(`  - ${gap.test_class}`);
    }
  }
  return lines.join("\n");
}
