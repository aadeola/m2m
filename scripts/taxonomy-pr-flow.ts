import { execSync } from "node:child_process";
import { writeFileSync } from "node:fs";
import { join } from "node:path";
import type { TaxonomyGapHandoff } from "./taxonomy-gap-handoff.ts";
import {
  formatTaxonomyHumanReport,
  type TaxonomyCoverageReport,
  uniqueMissingTestClasses,
} from "./taxonomy-coverage-core.ts";

export interface PrFlowOptions {
  repoRoot: string;
  branchName: string;
  handoff: TaxonomyGapHandoff;
  beforeReport: TaxonomyCoverageReport;
  afterReport: TaxonomyCoverageReport;
  agentSummary?: string;
}

export interface ValidationResult {
  success: boolean;
  command: string;
  output: string;
}

function execGit(repoRoot: string, args: string[], allowFailure = false): string {
  try {
    return execSync(`git ${args.join(" ")}`, {
      cwd: repoRoot,
      encoding: "utf8",
      stdio: ["ignore", "pipe", "pipe"],
    }).trim();
  } catch (err) {
    if (allowFailure) {
      return "";
    }
    const message = err instanceof Error ? err.message : String(err);
    throw new Error(`git ${args.join(" ")} failed: ${message}`);
  }
}

function commandExists(command: string): boolean {
  try {
    execSync(`command -v ${command}`, { stdio: "ignore" });
    return true;
  } catch {
    return false;
  }
}

export function generateBranchName(): string {
  const stamp = new Date().toISOString().replace(/[-:T.Z]/g, "").slice(0, 14);
  return `taxonomy-coverage/${stamp}`;
}

export function hasUncommittedChanges(repoRoot: string): boolean {
  const status = execGit(repoRoot, ["status", "--porcelain"], true);
  return status.length > 0;
}

export function getCurrentBranch(repoRoot: string): string {
  return execGit(repoRoot, ["rev-parse", "--abbrev-ref", "HEAD"]);
}

export function createBranchFromMain(repoRoot: string, branchName: string): void {
  if (hasUncommittedChanges(repoRoot)) {
    throw new Error(
      "Working tree has uncommitted changes. Commit or stash before running remediation.",
    );
  }

  execGit(repoRoot, ["fetch", "origin", "main"], true);
  execGit(repoRoot, ["checkout", "main"]);
  execGit(repoRoot, ["pull", "origin", "main"], true);
  execGit(repoRoot, ["checkout", "-b", branchName]);
}

export function runMavenValidation(
  repoRoot: string,
  report: TaxonomyCoverageReport,
): ValidationResult {
  const testClasses = uniqueMissingTestClasses(report);
  const command =
    testClasses.length > 0
      ? `mvn -q test -Dtest=${testClasses.join(",")}`
      : "mvn -q test";

  try {
    const output = execSync(command, {
      cwd: repoRoot,
      encoding: "utf8",
      stdio: ["ignore", "pipe", "pipe"],
      env: {
        ...process.env,
        MAVEN_OPTS: "-Dnet.bytebuddy.experimental=true",
      },
    });
    return { success: true, command, output };
  } catch (err) {
    const execErr = err as { stdout?: string; stderr?: string; message?: string };
    const output = [execErr.stdout ?? "", execErr.stderr ?? ""].filter(Boolean).join("\n");
    return { success: false, command, output: output || execErr.message || "mvn test failed" };
  }
}

export function commitGeneratedTests(repoRoot: string, message: string): boolean {
  execGit(repoRoot, ["add", "src/test/java/com/migration"]);
  execGit(repoRoot, ["add", "scripts/taxonomy-coverage-core.ts"], true);

  const staged = execGit(repoRoot, ["diff", "--cached", "--name-only"], true);
  if (!staged) {
    return false;
  }

  execGit(repoRoot, ["commit", "-m", JSON.stringify(message)]);
  return true;
}

export function pushBranch(repoRoot: string, branchName: string): void {
  execGit(repoRoot, ["push", "-u", "origin", branchName]);
}

export function buildPullRequestBody(options: PrFlowOptions): string {
  const { handoff, beforeReport, afterReport, agentSummary } = options;
  const lines: string[] = [];

  lines.push("## Summary");
  lines.push(
    "Automated taxonomy coverage remediation: adds missing tests across contract, routing, transform, and migration categories.",
  );
  lines.push("");
  lines.push("## Taxonomy coverage");
  lines.push(
    `- Before: ${beforeReport.implemented_buckets.length}/${beforeReport.total_buckets} buckets (${beforeReport.taxonomy_coverage_percent}%)`,
  );
  lines.push(
    `- After: ${afterReport.implemented_buckets.length}/${afterReport.total_buckets} buckets (${afterReport.taxonomy_coverage_percent}%)`,
  );
  lines.push("");

  lines.push("### By category (after)");
  for (const category of ["CONTRACT", "ROUTING", "TRANSFORM", "MIGRATION", "SMOKE"] as const) {
    const summary = afterReport.by_category[category];
    lines.push(`- **${category}**: ${summary.implemented}/${summary.total} (${summary.percent}%)`);
  }
  lines.push("");

  if (handoff.taxonomy_gaps.length > 0) {
    lines.push("## Tests added");
    for (const gap of handoff.taxonomy_gaps) {
      lines.push(`- \`${gap.test_class}\` (${gap.category}): ${gap.scenarios.join("; ")}`);
    }
    lines.push("");
  }

  if (handoff.contract_gaps.length > 0) {
    lines.push("## Contract call sites addressed");
    for (const gap of handoff.contract_gaps) {
      lines.push(`- ${gap.method} ${gap.endpoint} → \`${gap.suggested_test_class}\``);
    }
    lines.push("");
  }

  if (agentSummary) {
    lines.push("## Agent notes");
    lines.push(agentSummary);
    lines.push("");
  }

  lines.push("## Validation");
  lines.push(formatTaxonomyHumanReport(afterReport));
  lines.push("");
  lines.push("---");
  lines.push("*This PR was opened by the taxonomy-coverage SDK runner. Do not merge automatically.*");

  return lines.join("\n");
}

export function createPullRequest(options: PrFlowOptions): string {
  if (!commandExists("gh")) {
    throw new Error("GitHub CLI (gh) is required to create a pull request.");
  }

  const body = buildPullRequestBody(options);
  const bodyPath = join(options.repoRoot, ".taxonomy-coverage-pr-body.md");
  writeFileSync(bodyPath, body, "utf8");

  const title = `Add taxonomy coverage tests (${options.afterReport.taxonomy_coverage_percent}% build-out)`;
  const output = execSync(
    `gh pr create --base main --head ${options.branchName} --title ${JSON.stringify(title)} --body-file ${JSON.stringify(bodyPath)}`,
    { cwd: options.repoRoot, encoding: "utf8" },
  ).trim();

  return output;
}

/** Guardrail: never merge — this module intentionally has no merge function. */
export const NO_AUTO_MERGE = true as const;
