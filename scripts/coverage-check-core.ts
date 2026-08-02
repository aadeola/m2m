import { existsSync, readFileSync, readdirSync } from "node:fs";
import { join, resolve } from "node:path";

/** One client call site from inventory.json */
export interface InventoryEntry {
  endpoint: string;
  method: string;
  owning_service: string;
  source_file: string;
  path_params?: string[];
  query_params?: string[];
  request_fields?: string[];
  fields_used?: string[];
}

/** Endpoint contract key used for many-to-one matching */
export interface EndpointContract {
  method: string;
  endpoint: string;
  testClass: string;
}

/** Coverage report returned by the checker */
export interface CoverageReport {
  total_call_sites: number;
  covered_call_sites: CoveredCallSite[];
  uncovered_call_sites: UncoveredCallSite[];
  covered_endpoints: EndpointContract[];
  missing_endpoints: EndpointContract[];
  stub_test_suggestions: StubTestSuggestion[];
}

export interface CoveredCallSite extends InventoryEntry {
  test_class: string;
}

export interface UncoveredCallSite extends InventoryEntry {
  reason: string;
}

export interface StubTestSuggestion {
  method: string;
  endpoint: string;
  suggested_test_class: string;
  mongo_collection: string | null;
}

/**
 * Canonical endpoint/method → contract test class mapping.
 * Multiple inventory call sites may share one entry (many-to-one).
 */
export const ENDPOINT_CONTRACTS: EndpointContract[] = [
  { method: "GET", endpoint: "/products/{id}", testClass: "GetProductByIdContractTest" },
  { method: "GET", endpoint: "/orders/{id}", testClass: "GetOrderByIdContractTest" },
  {
    method: "GET",
    endpoint: "/orders?customer_id={customer_id}",
    testClass: "GetOrdersByCustomerIdContractTest",
  },
  { method: "POST", endpoint: "/orders", testClass: "PostOrderContractTest" },
  { method: "GET", endpoint: "/customers", testClass: "GetCustomersContractTest" },
  { method: "GET", endpoint: "/customers/{id}", testClass: "GetCustomerByIdContractTest" },
  { method: "GET", endpoint: "/orders", testClass: "GetOrdersContractTest" },
  { method: "GET", endpoint: "/orders/{id}/status", testClass: "GetOrderStatusContractTest" },
  {
    method: "GET",
    endpoint: "/customers/{id}/orders",
    testClass: "GetCustomerOrdersContractTest",
  },
];

const ENDPOINT_TO_COLLECTION: Record<string, string> = {
  "/products/{id}": "products",
  "/orders/{id}": "orders",
  "/orders?customer_id={customer_id}": "orders",
  "/orders": "orders",
  "/customers": "customers",
  "/customers/{id}": "customers",
  "/orders/{id}/status": "orders",
  "/customers/{id}/orders": "orders",
};

function contractKey(method: string, endpoint: string): string {
  return `${method.toUpperCase()} ${endpoint}`;
}

function buildContractLookup(): Map<string, EndpointContract> {
  const lookup = new Map<string, EndpointContract>();
  for (const contract of ENDPOINT_CONTRACTS) {
    lookup.set(contractKey(contract.method, contract.endpoint), contract);
  }
  return lookup;
}

function suggestTestClass(method: string, endpoint: string): string {
  const sanitized = endpoint
    .replace(/^\//, "")
    .replace(/\?.*$/, "")
    .replace(/\{[^}]+\}/g, "ById")
    .replace(/[^a-zA-Z0-9]+/g, " ")
    .trim()
    .split(/\s+/)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join("");

  const prefix = method === "GET" ? "Get" : method.charAt(0) + method.slice(1).toLowerCase();
  return `${prefix}${sanitized}ContractTest`;
}

export function loadInventory(repoRoot: string): InventoryEntry[] {
  const inventoryPath = join(repoRoot, "inventory.json");
  const raw = readFileSync(inventoryPath, "utf8");
  return JSON.parse(raw) as InventoryEntry[];
}

export function listContractTestClasses(repoRoot: string): Set<string> {
  const contractDir = join(repoRoot, "src/test/java/com/migration/contract");
  const files = readdirSync(contractDir);
  return new Set(
    files
      .filter((file) => file.endsWith("ContractTest.java"))
      .map((file) => file.replace(/\.java$/, "")),
  );
}

export function runCoverageCheck(repoRoot: string): CoverageReport {
  const resolvedRoot = resolve(repoRoot);
  const inventory = loadInventory(resolvedRoot);
  const testClasses = listContractTestClasses(resolvedRoot);
  const contractLookup = buildContractLookup();

  const covered_call_sites: CoveredCallSite[] = [];
  const uncovered_call_sites: UncoveredCallSite[] = [];
  const missingEndpointKeys = new Set<string>();
  const coveredEndpointKeys = new Set<string>();

  for (const entry of inventory) {
    const key = contractKey(entry.method, entry.endpoint);
    const contract = contractLookup.get(key);

    if (!contract) {
      uncovered_call_sites.push({
        ...entry,
        reason: `No endpoint contract mapping for ${entry.method} ${entry.endpoint}`,
      });
      missingEndpointKeys.add(key);
      continue;
    }

    if (!testClasses.has(contract.testClass)) {
      uncovered_call_sites.push({
        ...entry,
        reason: `Expected contract test ${contract.testClass}.java not found`,
      });
      missingEndpointKeys.add(key);
      continue;
    }

    covered_call_sites.push({ ...entry, test_class: contract.testClass });
    coveredEndpointKeys.add(key);
  }

  const covered_endpoints = ENDPOINT_CONTRACTS.filter((contract) =>
    coveredEndpointKeys.has(contractKey(contract.method, contract.endpoint)),
  );

  const missing_endpoints = ENDPOINT_CONTRACTS.filter((contract) =>
    missingEndpointKeys.has(contractKey(contract.method, contract.endpoint)),
  );

  const stub_test_suggestions: StubTestSuggestion[] = missing_endpoints.map((contract) => ({
    method: contract.method,
    endpoint: contract.endpoint,
    suggested_test_class: contract.testClass,
    mongo_collection: ENDPOINT_TO_COLLECTION[contract.endpoint] ?? null,
  }));

  // Also suggest stubs for uncovered call sites whose endpoint isn't in ENDPOINT_CONTRACTS
  for (const uncovered of uncovered_call_sites) {
    const key = contractKey(uncovered.method, uncovered.endpoint);
    if (!missingEndpointKeys.has(key)) {
      continue;
    }
    if (stub_test_suggestions.some((s) => contractKey(s.method, s.endpoint) === key)) {
      continue;
    }
    stub_test_suggestions.push({
      method: uncovered.method,
      endpoint: uncovered.endpoint,
      suggested_test_class: suggestTestClass(uncovered.method, uncovered.endpoint),
      mongo_collection: ENDPOINT_TO_COLLECTION[uncovered.endpoint] ?? null,
    });
  }

  return {
    total_call_sites: inventory.length,
    covered_call_sites,
    uncovered_call_sites,
    covered_endpoints,
    missing_endpoints,
    stub_test_suggestions,
  };
}

export function formatHumanReport(report: CoverageReport): string {
  const lines: string[] = [];
  lines.push("Coverage Check Report");
  lines.push("=====================");
  lines.push(`Total call sites: ${report.total_call_sites}`);
  lines.push(`Covered: ${report.covered_call_sites.length}`);
  lines.push(`Uncovered: ${report.uncovered_call_sites.length}`);
  lines.push("");

  if (report.covered_endpoints.length > 0) {
    lines.push("Covered endpoint contracts:");
    for (const endpoint of report.covered_endpoints) {
      lines.push(`  - ${endpoint.method} ${endpoint.endpoint} -> ${endpoint.testClass}`);
    }
    lines.push("");
  }

  if (report.uncovered_call_sites.length > 0) {
    lines.push("Uncovered call sites:");
    for (const site of report.uncovered_call_sites) {
      lines.push(
        `  - ${site.method} ${site.endpoint} (${site.owning_service}, ${site.source_file})`,
      );
      lines.push(`    reason: ${site.reason}`);
    }
    lines.push("");
    lines.push("Do not cut over until all call sites have contract test coverage.");
  } else {
    lines.push("All inventory call sites are covered by contract tests.");
  }

  return lines.join("\n");
}

export function isCoverageComplete(report: CoverageReport): boolean {
  return report.uncovered_call_sites.length === 0;
}

/** Verify repo paths exist before running */
export function validateRepoLayout(repoRoot: string): void {
  const inventoryPath = join(repoRoot, "inventory.json");
  const contractDir = join(repoRoot, "src/test/java/com/migration/contract");

  if (!existsSync(inventoryPath)) {
    throw new Error(`inventory.json not found at ${inventoryPath}`);
  }
  if (!existsSync(contractDir)) {
    throw new Error(`Contract test directory not found at ${contractDir}`);
  }
}
