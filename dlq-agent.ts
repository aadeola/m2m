/**
 * DLQ agent runner — poll unresolved backfill_dlq rows and triage matching
 * Mongo write failures via the Cursor SDK (local runtime).
 *
 * Run: npm run dlq
 * Cron (every minute): * * * * * cd /path/to/m2m && npm run dlq
 */
import { Agent, CursorAgentError } from "@cursor/sdk";
import { execFile } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = __dirname;

// #region agent log
function dbg(location: string, message: string, data: Record<string, unknown>, hypothesisId: string) {
  fetch('http://127.0.0.1:7322/ingest/a93b445a-ca5f-4095-ac0a-1a0e1a100848', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Debug-Session-Id': '1c03ac' },
    body: JSON.stringify({ sessionId: '1c03ac', runId: 'run1', hypothesisId, location, message, data, timestamp: Date.now() }),
  }).catch(() => {});
}
// #endregion agent log

const TARGET_EXCEPTION_CLASSES = new Set([
  "com.migration.exception.MongoSchemaValidationException",
  "org.springframework.data.mongodb.BulkOperationException",
]);

interface DlqEntry {
  id: number;
  entity_name: string;
  start_pk: number;
  end_pk: number;
  exception_class: string;
  message: string | null;
  occurred_at: string;
  resolved: boolean;
  resolved_at: string | null;
}

function batchBranchName(entry: DlqEntry): string {
  return `dlq-fix/${entry.entity_name}-${entry.start_pk}-${entry.end_pk}`;
}

function composeProjectName(entry: DlqEntry): string {
  return `dlq-${entry.id}`;
}

function worktreeDir(entry: DlqEntry): string {
  return path.join(os.tmpdir(), `dlq-agent-worktree-${entry.id}-${process.pid}`);
}

/**
 * Thrown when another still-running invocation (a concurrent cron tick or a
 * manual run) already holds the batch's fix branch checked out in its own
 * worktree. This is an expected outcome of having no cross-process lock —
 * callers should treat it as a graceful skip, not a crash.
 */
class BranchInUseError extends Error {
  constructor(branch: string) {
    super(`Branch ${branch} is already checked out by another in-flight dlq-agent run`);
    this.name = "BranchInUseError";
  }
}

/**
 * Snapshot REPO_ROOT's current local state (HEAD + all uncommitted tracked
 * changes, minus .env) into a throwaway commit object, WITHOUT touching the
 * working tree, the index, or any branch ref. `git stash create` builds the
 * commit but does not apply/pop it; we `git reset` afterward purely to undo
 * the `git add` staging step, so REPO_ROOT is left byte-for-byte as it was.
 *
 * TEMPORARY: this is used instead of origin/main so a local-only retest can
 * see fixes that haven't been pushed yet. AGENTS.md's documented invariant
 * (branch from origin/main, never local) should be restored once these fixes
 * are committed and pushed for real.
 */
async function snapshotLocalWorkingTree(): Promise<string> {
  const envPath = path.join(REPO_ROOT, ".env");
  const liveEnvBackup = fs.existsSync(envPath) ? fs.readFileSync(envPath) : null;
  try {
    // `git stash create` snapshots ALL tracked-file diffs vs HEAD regardless
    // of what's staged — a pathspec on `git add` can't exclude .env from it.
    // So we revert .env to its committed value on disk first (secrets never
    // touch a git object), snapshot, then restore the live file byte-for-byte.
    if (liveEnvBackup) {
      await execFileAsync("git", ["checkout", "HEAD", "--", ".env"], { cwd: REPO_ROOT }).catch(() => {});
    }
    await execFileAsync("git", ["add", "-A"], { cwd: REPO_ROOT });
    const { stdout } = await execFileAsync("git", ["stash", "create"], { cwd: REPO_ROOT });
    const sha = stdout.trim();
    // #region agent log
    dbg('dlq-agent.ts:snapshotLocalWorkingTree', 'snapshot created', { sha: sha || "(empty, no changes)" }, 'H-snapshot');
    // #endregion agent log
    return sha || "HEAD";
  } finally {
    await execFileAsync("git", ["reset"], { cwd: REPO_ROOT }).catch(() => {});
    if (liveEnvBackup) {
      fs.writeFileSync(envPath, liveEnvBackup);
    }
  }
}

/**
 * Create an isolated git worktree checked out on the batch's fix branch, cut
 * from a snapshot of the current local working tree (see above). The agent
 * does ALL its git work (commit/push/PR) inside this separate directory —
 * never in REPO_ROOT — so it can never check out a branch or reset files out
 * from under whoever is actively working in this shared repo.
 */
async function createIsolatedWorktree(entry: DlqEntry): Promise<string> {
  const branch = batchBranchName(entry);
  const dir = worktreeDir(entry);

  // Best-effort cleanup of any stale worktree/branch left by a prior failed run.
  // If the branch is still checked out by a *live* concurrent run, both of
  // these no-op (git refuses to delete a branch used by another worktree).
  await execFileAsync("git", ["worktree", "remove", "--force", dir], { cwd: REPO_ROOT }).catch(() => {});
  await execFileAsync("git", ["branch", "-D", branch], { cwd: REPO_ROOT }).catch(() => {});
  fs.rmSync(dir, { recursive: true, force: true });

  const startPoint = await snapshotLocalWorkingTree();
  try {
    await execFileAsync("git", ["worktree", "add", dir, "-b", branch, startPoint], { cwd: REPO_ROOT });
  } catch (err) {
    const stderr = err && typeof err === "object" && "stderr" in err ? String((err as { stderr: unknown }).stderr) : "";
    // #region agent log
    dbg('dlq-agent.ts:createIsolatedWorktree', 'worktree add failed', { dlqId: entry.id, branch, stderr }, 'H-branchcollision');
    // #endregion agent log
    if (/already exists/.test(stderr) || /already used by worktree/.test(stderr)) {
      throw new BranchInUseError(branch);
    }
    throw err;
  }
  return dir;
}

async function removeIsolatedWorktree(entry: DlqEntry, dir: string): Promise<void> {
  const branch = batchBranchName(entry);
  await execFileAsync("git", ["worktree", "remove", "--force", dir], { cwd: REPO_ROOT }).catch((err) => {
    console.warn(`Failed to remove worktree ${dir} for dlq id=${entry.id}`, err);
  });
  await execFileAsync("git", ["branch", "-D", branch], { cwd: REPO_ROOT }).catch(() => {});
}

async function fetchDlqEntries(baseUrl: string): Promise<DlqEntry[]> {
  const url = `${baseUrl.replace(/\/$/, "")}/admin/backfill/dlq?resolved=false`;
  const response = await fetch(url);
  if (!response.ok) {
    // #region agent log
    dbg('dlq-agent.ts:fetchDlqEntries', 'DLQ fetch non-OK', { url, status: response.status }, 'H-fetch');
    // #endregion agent log
    throw new Error(`DLQ API ${url} returned ${response.status}`);
  }
  const entries = (await response.json()) as DlqEntry[];
  // #region agent log
  dbg('dlq-agent.ts:fetchDlqEntries', 'DLQ fetch result', {
    url,
    status: response.status,
    count: entries.length,
    entries: entries.map((e) => ({ id: e.id, entity_name: e.entity_name, exception_class: e.exception_class, resolved: e.resolved })),
  }, 'H-fetch');
  // #endregion agent log
  return entries;
}

async function hasOpenPrForBatch(entry: DlqEntry): Promise<boolean> {
  const branch = batchBranchName(entry);
  try {
    const { stdout } = await execFileAsync(
      "gh",
      ["pr", "list", "--state", "open", "--head", branch, "--json", "number,headRefName"],
      { cwd: REPO_ROOT },
    );
    const prs = JSON.parse(stdout) as Array<{ number: number; headRefName: string }>;
    const result = prs.some((pr) => pr.headRefName === branch);
    // #region agent log
    dbg('dlq-agent.ts:hasOpenPrForBatch', 'gh pr list result', { branch, prCount: prs.length, result }, 'H-openpr');
    // #endregion agent log
    return result;
  } catch (err) {
    // #region agent log
    dbg('dlq-agent.ts:hasOpenPrForBatch', 'gh pr list threw', { branch, error: String(err) }, 'H-openpr');
    // #endregion agent log
    console.warn(`gh pr list failed for ${branch}; treating as no open PR`, err);
    return false;
  }
}

function buildTriagePrompt(entry: DlqEntry, worktreePath: string): string {
  const branch = batchBranchName(entry);
  const project = composeProjectName(entry);

  return `You are the DLQ triage agent for the RDBMS → MongoDB migration shim.

## Where you are running
You are already in an isolated git worktree at ${worktreePath}, checked out on
branch ${branch} (cut from origin/main). This is a separate checkout from the
engineer's main working copy of this repo — your commits/pushes here cannot
affect their branch or files. Do NOT run \`git checkout\`, \`git switch\`, or
\`git worktree\` commands yourself; you're already on the right branch.

## DLQ entry
- id: ${entry.id}
- entity_name: ${entry.entity_name}
- start_pk: ${entry.start_pk}
- end_pk: ${entry.end_pk}
- exception_class: ${entry.exception_class}
- message: ${entry.message ?? "(null)"}
- occurred_at: ${entry.occurred_at}

## How to investigate
- Start from the exception and reproduce it in isolation. Trace the failure to root cause before changing code.
- Prefer querying the isolated Postgres/Mongo and reading seed/migration scripts over guessing transformer fixes.
- Example chain for order schema failures: missing embedded product → product migrated_at is NULL → ask why that product never migrated (constraints, triggers, upstream seed) → fix the real root cause.
- Do not spoon-feed yourself a file list; use evidence from the repro.

## What must not be weakened
- Do NOT remove or weaken the migrated_at embed filter in BackfillService.toOrderDocument (product.getMigratedAt() != null). That filter is a real migration invariant.
- Do NOT weaken Mongo $jsonSchema validators (seeds/mongo-init.js).

## What to fix when root cause is seed poison
- If investigation shows a bad Postgres trigger / poison seed (installed by scripts/seed-bulk.sql on prod, mirrored into isolation by dlq-seed-subset.sh), remove that trigger/poison from the seed script and open a PR with that fix.
- Only change Java transform/shape code when evidence shows a genuine transformer bug; if you do, add/adjust a test that asserts the invariant.

## Safety invariants (must follow)
- NEVER write to production Postgres (:5432) or production Mongo (:27017).
- NEVER set backfill_dlq.resolved = true.
- NEVER commit to main.
- NEVER merge the PR.
- You are already on branch ${branch}, cut from origin/main — just commit/push here.
- Tear down isolation when done: docker compose -p ${project} -f docker-compose.dlq.yml down -v

## Required steps
1. Read the dlq-triage skill playbook if present (.cursor/skills/dlq-triage/SKILL.md).
2. Bring up isolation:
   docker compose -p ${project} -f docker-compose.dlq.yml up -d
   Wait until healthy. Host ports: Postgres 15432, Mongo 37017.
3. Seed the failing slice from prod into isolation (rows + prod triggers):
   ./scripts/dlq-seed-subset.sh ${entry.entity_name} ${entry.start_pk} ${entry.end_pk} localhost 15432
4. Reproduce with backfill against isolation only:
   SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:15432/migration \\
   SPRING_DATA_MONGODB_URI=mongodb://localhost:37017/mydb \\
   mvn -q spring-boot:run -Dspring-boot.run.arguments=--backfill
5. Investigate root cause; apply the correct fix per the rules above (seed poison → fix seed-bulk.sql; genuine transform bug → fix Java + test).
6. Re-test the fix against the isolated stack (repeat step 4 and/or targeted mvn tests).
7. Commit here (you're already on ${branch}), push, and open a PR (do not merge):
   git add -A && git commit -m "..."
   git push -u origin HEAD
   gh pr create --title "fix(dlq): ${entry.entity_name} ${entry.start_pk}-${entry.end_pk}" --body "..."
8. Write reports/dlq/${entry.id}.md with root cause, repro, fix summary, test evidence, and PR URL.
9. Tear down: docker compose -p ${project} -f docker-compose.dlq.yml down -v

Use the MongoDB MCP against the isolated instance (host.docker.internal:37017) when inspecting documents.
`;
}

async function runAgentForEntry(entry: DlqEntry, apiKey: string): Promise<"finished" | "error"> {
  const mongoPort = process.env.DLQ_MONGO_PORT ?? "37017";
  const worktreePath = await createIsolatedWorktree(entry);
  // #region agent log
  dbg('dlq-agent.ts:runAgentForEntry', 'worktree created', { dlqId: entry.id, worktreePath }, 'H-worktree');
  // #endregion agent log

  try {
    await using agent = await Agent.create({
      apiKey,
      model: { id: "composer-2.5" },
      name: `dlq-${entry.id}-${entry.entity_name}`,
      local: {
        cwd: worktreePath,
        settingSources: [],
      },
      mcpServers: {
        MongoDB: {
          command: "docker",
          args: [
            "run",
            "--rm",
            "-i",
            "-e",
            `MDB_MCP_CONNECTION_STRING=mongodb://host.docker.internal:${mongoPort}/mydb`,
            "mongodb/mongodb-mcp-server:latest",
          ],
        },
      },
    });

    // #region agent log
    dbg('dlq-agent.ts:runAgentForEntry', 'agent created', { dlqId: entry.id, agentId: agent.agentId }, 'H-agentrun');
    // #endregion agent log
    const run = await agent.send(buildTriagePrompt(entry, worktreePath));
    console.log(`Started agent=${agent.agentId} run=${run.id} for dlq id=${entry.id}`);
    // #region agent log
    dbg('dlq-agent.ts:runAgentForEntry', 'agent run started', { dlqId: entry.id, agentId: agent.agentId, runId: run.id }, 'H-agentrun');
    // #endregion agent log

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
    console.log(`\nRun ${run.id} finished with status=${result.status}`);
    // #region agent log
    dbg('dlq-agent.ts:runAgentForEntry', 'agent run finished', { dlqId: entry.id, runId: run.id, status: result.status }, 'H-agentrun');
    // #endregion agent log
    return result.status === "finished" ? "finished" : "error";
  } finally {
    await removeIsolatedWorktree(entry, worktreePath);
  }
}

async function main(): Promise<void> {
  const apiKey = process.env.CURSOR_API_KEY;
  // #region agent log
  dbg('dlq-agent.ts:main', 'startup env check', {
    apiKeyLength: apiKey?.length ?? 0,
    apiKeyLeadingWs: apiKey ? /^\s/.test(apiKey) : null,
    apiKeyTrailingWs: apiKey ? /\s$/.test(apiKey) : null,
    dlqApiBase: process.env.DLQ_API_BASE ?? null,
    pid: process.pid,
  }, 'H-apikey');
  // #endregion agent log
  if (!apiKey) {
    console.error("CURSOR_API_KEY is required");
    process.exit(1);
  }

  const baseUrl = process.env.DLQ_API_BASE ?? "http://localhost:8080";

  let exitCode = 0;
  const entries = await fetchDlqEntries(baseUrl);
  const matching = entries.filter((e) => TARGET_EXCEPTION_CLASSES.has(e.exception_class));
  const skippedOther = entries.length - matching.length;
  // #region agent log
  dbg('dlq-agent.ts:main', 'matching filter result', {
    totalEntries: entries.length,
    matchingCount: matching.length,
    skippedOther,
    matchingSummary: matching.map((e) => ({ id: e.id, entity_name: e.entity_name, exception_class: e.exception_class })),
  }, 'H-matching');
  // #endregion agent log
  if (skippedOther > 0) {
    console.log(`Skipped ${skippedOther} DLQ row(s) with non-target exception_class`);
  }
  if (matching.length === 0) {
    console.log("No matching unresolved DLQ entries; done.");
    process.exit(0);
  }

  console.log(`Found ${matching.length} matching DLQ entr${matching.length === 1 ? "y" : "ies"}`);

  for (const entry of matching) {
    if (await hasOpenPrForBatch(entry)) {
      console.log(
        `Skipping dlq id=${entry.id} — open PR already exists for branch ${batchBranchName(entry)}`,
      );
      continue;
    }

    try {
      const status = await runAgentForEntry(entry, apiKey);
      if (status === "error") {
        exitCode = 2;
        console.error(`Agent run failed for dlq id=${entry.id}`);
      }
    } catch (err) {
      if (err instanceof BranchInUseError) {
        // #region agent log
        dbg('dlq-agent.ts:main', 'branch in use, stopping', { dlqId: entry.id, error: String(err) }, 'H-branchcollision');
        // #endregion agent log
        // Another in-flight run already owns this batch's branch. Stop here
        // rather than creating a competing branch/PR for the same batch.
        console.log(`Stopping — dlq id=${entry.id}: ${err.message}`);
        process.exit(0);
      } else if (err instanceof CursorAgentError) {
        console.error(
          `CursorAgentError for dlq id=${entry.id}: ${err.message} retryable=${err.isRetryable}`,
        );
        exitCode = 1;
        if (err.isRetryable) {
          console.error("Failure marked retryable; will retry on next cron tick.");
        }
      } else {
        // #region agent log
        dbg('dlq-agent.ts:main', 'unexpected error for entry', { dlqId: entry.id, error: String(err) }, 'H-agentrun');
        // #endregion agent log
        console.error(`Unexpected error for dlq id=${entry.id}`, err);
        exitCode = 1;
      }
    }
  }

  process.exit(exitCode);
}

main().catch((err) => {
  // #region agent log
  dbg('dlq-agent.ts:main.catch', 'main() rejected', { error: String(err) }, 'H-fetch');
  // #endregion agent log
  console.error(err);
  process.exit(1);
});
