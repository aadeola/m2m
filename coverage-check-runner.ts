import { resolve } from "node:path";
import {
  type CoverageReport,
  formatHumanReport,
  isCoverageComplete,
  runCoverageCheck,
  validateRepoLayout,
} from "./scripts/coverage-check-core.ts";

const COVERAGE_CHECK_PROMPT = `Run the coverage-check skill for this repository.

1. Read inventory.json and verify every call site has a contract test.
2. Match by method + endpoint (many inventory entries may map to one contract test).
3. Use InventoryCatalog.java and scripts/coverage-check-core.ts as the canonical mapping.
4. For any uncovered call site, use the MongoDB MCP server to pull a sample document
   and suggest a stub contract test with real field names.
5. End your response with a JSON block (in \`\`\`json fences) matching this schema:
   {
     "total_call_sites": number,
     "covered_call_sites": [{ "endpoint", "method", "owning_service", "source_file", "test_class" }],
     "uncovered_call_sites": [{ "endpoint", "method", "owning_service", "source_file", "reason" }],
     "covered_endpoints": [{ "method", "endpoint", "testClass" }],
     "missing_endpoints": [{ "method", "endpoint", "testClass" }],
     "stub_test_suggestions": [{ "method", "endpoint", "suggested_test_class", "mongo_collection" }]
   }`;

function parseArgs(argv: string[]): { localOnly: boolean; json: boolean } {
  return {
    localOnly: argv.includes("--local-only"),
    json: argv.includes("--json"),
  };
}

function extractJsonBlock(text: string): CoverageReport | null {
  const fenceMatch = text.match(/```json\s*([\s\S]*?)```/);
  if (!fenceMatch) {
    return null;
  }

  try {
    return JSON.parse(fenceMatch[1].trim()) as CoverageReport;
  } catch {
    return null;
  }
}

async function runAgentCoverageCheck(repoRoot: string): Promise<{
  agentText: string;
  parsed: CoverageReport | null;
}> {
  const { Agent } = await import("@cursor/sdk");

  const apiKey = process.env.CURSOR_API_KEY;
  if (!apiKey) {
    throw new Error("CURSOR_API_KEY environment variable is required");
  }

  await using agent = await Agent.create({
    apiKey,
    model: { id: "composer-2.5" },
    local: { cwd: repoRoot, settingSources: [] },
  });

  const run = await agent.send(COVERAGE_CHECK_PROMPT);

  for await (const event of run.stream()) {
    if (event.type === "assistant") {
      for (const block of event.message.content) {
        if (block.type === "text") {
          process.stdout.write(block.text);
        }
      }
    }
  }

  const result = await run.wait();
  if (result.status === "error") {
    throw new Error(`Coverage check agent run failed: ${result.id}`);
  }

  const agentText = result.result ?? "";
  return { agentText, parsed: extractJsonBlock(agentText) };
}

function printReport(report: CoverageReport, json: boolean): void {
  if (json) {
    console.log(JSON.stringify(report, null, 2));
    return;
  }

  console.log(formatHumanReport(report));

  if (report.stub_test_suggestions.length > 0) {
    console.log("");
    console.log("Stub test suggestions:");
    for (const suggestion of report.stub_test_suggestions) {
      console.log(
        `  - ${suggestion.method} ${suggestion.endpoint} -> ${suggestion.suggested_test_class}` +
          (suggestion.mongo_collection ? ` (collection: ${suggestion.mongo_collection})` : ""),
      );
    }
  }
}

function exitForReport(report: CoverageReport): never {
  if (isCoverageComplete(report)) {
    process.exit(0);
  }
  process.exit(1);
}

async function main(): Promise<void> {
  const repoRoot = resolve(process.cwd());
  const { localOnly, json } = parseArgs(process.argv.slice(2));

  validateRepoLayout(repoRoot);
  const localReport = runCoverageCheck(repoRoot);

  if (localOnly) {
    printReport(localReport, json);
    exitForReport(localReport);
  }

  // Always print the deterministic local summary first.
  if (!json) {
    console.log("Local coverage check:");
    console.log(formatHumanReport(localReport));
    console.log("");
  }

  if (!process.env.CURSOR_API_KEY) {
    console.warn(
      "CURSOR_API_KEY not set — skipping Cursor SDK agent invocation. " +
        "Use --local-only to run without the SDK, or set CURSOR_API_KEY for the full workflow.",
    );
    printReport(localReport, json);
    exitForReport(localReport);
  }

  if (!json) {
    console.log("Running Cursor SDK coverage-check agent...");
    console.log("");
  }

  try {
    const { parsed } = await runAgentCoverageCheck(repoRoot);

    const finalReport = parsed ?? localReport;
    if (!parsed && !json) {
      console.warn("");
      console.warn(
        "Could not parse structured JSON from agent response; using local check result for exit code.",
      );
    }

    if (json) {
      printReport(finalReport, true);
    }

    exitForReport(finalReport);
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    if (message.includes("CURSOR_API_KEY") || err?.constructor?.name === "CursorAgentError") {
      console.error(`SDK startup failed: ${message}`);
      process.exit(1);
    }
    throw err;
  }
}

main().catch((err: unknown) => {
  console.error(err instanceof Error ? err.message : String(err));
  process.exit(1);
});
