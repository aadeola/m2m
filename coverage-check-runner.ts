import { execSync } from "node:child_process";
import { resolve } from "node:path";
import {
  formatTaxonomyHumanReport,
  isTaxonomyCoverageComplete,
  runTaxonomyCoverageCheck,
  type TaxonomyCoverageReport,
  validateTaxonomySource,
} from "./scripts/taxonomy-coverage-core.ts";
import { validateRepoLayout } from "./scripts/coverage-check-core.ts";
import {
  buildGapHandoff,
  buildRemediationPrompt,
  formatGapHandoffSummary,
} from "./scripts/taxonomy-gap-handoff.ts";
import {
  commitGeneratedTests,
  createBranchFromMain,
  createPullRequest,
  generateBranchName,
  getCurrentBranch,
  runMavenValidation,
  pushBranch,
} from "./scripts/taxonomy-pr-flow.ts";

interface RunnerOptions {
  localOnly: boolean;
  json: boolean;
  dryRun: boolean;
  noPr: boolean;
}

function parseArgs(argv: string[]): RunnerOptions {
  return {
    localOnly: argv.includes("--local-only"),
    json: argv.includes("--json"),
    dryRun: argv.includes("--dry-run"),
    noPr: argv.includes("--no-pr"),
  };
}

function printReport(report: TaxonomyCoverageReport, json: boolean): void {
  if (json) {
    console.log(JSON.stringify(report, null, 2));
    return;
  }
  console.log(formatTaxonomyHumanReport(report));
}

function exitForReport(report: TaxonomyCoverageReport): never {
  process.exit(isTaxonomyCoverageComplete(report) ? 0 : 1);
}

async function runRemediationAgent(
  repoRoot: string,
  prompt: string,
): Promise<{ agentText: string; agentSummary: string }> {
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

  const run = await agent.send(prompt);

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
    throw new Error(`Taxonomy remediation agent run failed: ${result.id}`);
  }

  const agentText = result.result ?? "";
  const summaryMatch = agentText.match(/```json\s*([\s\S]*?)```/);
  let agentSummary = "";
  if (summaryMatch) {
    try {
      agentSummary = JSON.stringify(JSON.parse(summaryMatch[1].trim()), null, 2);
    } catch {
      agentSummary = summaryMatch[1].trim();
    }
  }

  return { agentText, agentSummary };
}

async function runRemediationWorkflow(
  repoRoot: string,
  beforeReport: TaxonomyCoverageReport,
  options: RunnerOptions,
): Promise<void> {
  const handoff = buildGapHandoff(beforeReport);
  const branchName = generateBranchName();

  if (!options.json) {
    console.log(formatGapHandoffSummary(handoff));
    console.log("");
  }

  if (options.dryRun) {
    if (options.json) {
      console.log(JSON.stringify({ handoff, branchName, prompt: buildRemediationPrompt(handoff, branchName) }, null, 2));
    } else {
      console.log(`Dry run — would create branch: ${branchName}`);
      console.log("");
      console.log(buildRemediationPrompt(handoff, branchName));
    }
    process.exit(1);
  }

  if (!process.env.CURSOR_API_KEY) {
    console.error(
      "CURSOR_API_KEY is required for remediation. Use --local-only for gate-only mode.",
    );
    process.exit(1);
  }

  const previousBranch = getCurrentBranch(repoRoot);

  try {
    if (!options.json) {
      console.log(`Creating branch ${branchName} from main...`);
    }
    createBranchFromMain(repoRoot, branchName);

    const prompt = buildRemediationPrompt(handoff, branchName);
    if (!options.json) {
      console.log("Running SDK agent to draft missing tests...");
      console.log("");
    }

    const { agentSummary } = await runRemediationAgent(repoRoot, prompt);

    if (!options.json) {
      console.log("");
      console.log("Running targeted Maven validation...");
    }

    const validation = runMavenValidation(repoRoot, beforeReport);
    if (!validation.success && !options.json) {
      console.warn("Maven validation failed:");
      console.warn(validation.output.slice(-2000));
      console.warn("Continuing to re-scan coverage and commit any generated tests.");
    }

    const afterReport = runTaxonomyCoverageCheck(repoRoot);

    if (!options.json) {
      console.log("");
      console.log("After remediation:");
      console.log(formatTaxonomyHumanReport(afterReport));
      console.log("");
    }

    const committed = commitGeneratedTests(
      repoRoot,
      `Add taxonomy coverage tests (${afterReport.taxonomy_coverage_percent}% build-out)\n\nAutomated remediation for missing contract, routing, transform, and migration test buckets.`,
    );

    if (!committed && !options.json) {
      console.warn("No test files were committed — agent may not have created new tests.");
    }

    if (!options.noPr && committed) {
      if (!options.json) {
        console.log(`Pushing branch ${branchName}...`);
      }
      pushBranch(repoRoot, branchName);

      const prUrl = createPullRequest({
        repoRoot,
        branchName,
        handoff,
        beforeReport,
        afterReport,
        agentSummary,
      });

      if (!options.json) {
        console.log("");
        console.log(`Pull request created: ${prUrl}`);
        console.log("Workflow complete — PR was not merged automatically.");
      }
    } else if (!options.noPr && !committed && !options.json) {
      console.warn("Skipping PR creation because no tests were committed.");
    }

    if (options.json) {
      console.log(
        JSON.stringify(
          {
            branch: branchName,
            before: beforeReport,
            after: afterReport,
            committed,
            pr_skipped: options.noPr || !committed,
          },
          null,
          2,
        ),
      );
    }

    exitForReport(afterReport);
  } catch (err) {
    if (!options.json) {
      console.error(`Remediation failed on branch ${branchName}. Attempting to restore ${previousBranch}...`);
    }
    try {
      execGitRestore(repoRoot, previousBranch);
    } catch {
      // best-effort restore
    }
    throw err;
  }
}

function execGitRestore(repoRoot: string, branch: string): void {
  execSync(`git checkout ${branch}`, { cwd: repoRoot, stdio: "ignore" });
}

async function main(): Promise<void> {
  const repoRoot = resolve(process.cwd());
  const options = parseArgs(process.argv.slice(2));

  validateRepoLayout(repoRoot);
  validateTaxonomySource(repoRoot);
  const report = runTaxonomyCoverageCheck(repoRoot);

  if (options.localOnly) {
    printReport(report, options.json);
    exitForReport(report);
  }

  if (isTaxonomyCoverageComplete(report)) {
    if (!options.json) {
      console.log("Taxonomy coverage is complete — no remediation needed.");
      console.log("");
    }
    printReport(report, options.json);
    process.exit(0);
  }

  await runRemediationWorkflow(repoRoot, report, options);
}

main().catch((err: unknown) => {
  console.error(err instanceof Error ? err.message : String(err));
  process.exit(1);
});
