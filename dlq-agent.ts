/**
 * DLQ agent runner — poll unresolved backfill_dlq rows and triage matching
 * Mongo write failures via the Cursor SDK (local runtime).
 *
 * Run: npm run dlq
 * Cron (every minute): * * * * * cd /path/to/m2m && npm run dlq
 */
import { Agent, CursorAgentError } from "@cursor/sdk";
import { execFile } from "node:child_process";
import { promises as fs, rmSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = __dirname;
const LOCK_PATH = path.join(REPO_ROOT, ".dlq-agent.lock");

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

async function fetchDlqEntries(baseUrl: string): Promise<DlqEntry[]> {
  const url = `${baseUrl.replace(/\/$/, "")}/admin/backfill/dlq?resolved=false`;
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`DLQ API ${url} returned ${response.status}`);
  }
  return (await response.json()) as DlqEntry[];
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
    return prs.some((pr) => pr.headRefName === branch);
  } catch (err) {
    console.warn(`gh pr list failed for ${branch}; treating as no open PR`, err);
    return false;
  }
}

function buildTriagePrompt(entry: DlqEntry): string {
  const branch = batchBranchName(entry);
  const project = composeProjectName(entry);
  const isSchema =
    entry.exception_class === "com.migration.exception.MongoSchemaValidationException";

  return `You are the DLQ triage agent for the RDBMS → MongoDB migration shim.

## DLQ entry
- id: ${entry.id}
- entity_name: ${entry.entity_name}
- start_pk: ${entry.start_pk}
- end_pk: ${entry.end_pk}
- exception_class: ${entry.exception_class}
- message: ${entry.message ?? "(null)"}
- occurred_at: ${entry.occurred_at}

## Classification hint
${
  isSchema
    ? "This is a MongoDB schema validation failure (code 121 / validationAction=error). Inspect seeds/mongo-init.js orders validator and the OrderTransformer / embed path."
    : "This is a BulkOperationException (non-schema or pre-wrap bulk write failure). Inspect the message/cause — may still be validation-related, or connectivity/duplicate-key/etc."
}

## Safety invariants (must follow)
- NEVER write to production Postgres (:5432) or production Mongo (:27017).
- NEVER set backfill_dlq.resolved = true.
- NEVER commit to main.
- Branch name MUST be exactly: ${branch}
- Create that branch from origin/main only (fetch first; do NOT base it on local main).
- Tear down isolation when done: docker compose -p ${project} -f docker-compose.dlq.yml down -v

## Required steps
1. Read the dlq-triage skill playbook if present (.cursor/skills/dlq-triage/SKILL.md).
2. Bring up isolation:
   docker compose -p ${project} -f docker-compose.dlq.yml up -d
   Wait until healthy. Host ports: Postgres 15432, Mongo 37017.
3. Seed the failing slice from prod into isolation:
   ./scripts/dlq-seed-subset.sh ${entry.entity_name} ${entry.start_pk} ${entry.end_pk} localhost 15432
4. Reproduce with backfill against isolation only:
   SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:15432/migration \\
   SPRING_DATA_MONGODB_URI=mongodb://localhost:37017/mydb \\
   mvn -q spring-boot:run -Dspring-boot.run.arguments=--backfill
5. Fix the Java code so documents satisfy the schema / bulk write succeeds (prefer transformer/embed fixes over weakening validators unless justified).
6. Re-test the fix against the isolated stack (repeat step 4 and/or targeted mvn tests).
7. Create branch ${branch} from origin/main, commit, push, and open a PR:
   git fetch origin main
   git checkout -B ${branch} origin/main
   git push -u origin HEAD
   gh pr create --title "fix(dlq): ${entry.entity_name} ${entry.start_pk}-${entry.end_pk}" --body "..."
8. Write reports/dlq/${entry.id}.md with root cause, repro, fix summary, test evidence, and PR URL.
9. Tear down: docker compose -p ${project} -f docker-compose.dlq.yml down -v

Use the MongoDB MCP against the isolated instance (host.docker.internal:37017) when inspecting documents.
`;
}

async function runAgentForEntry(entry: DlqEntry, apiKey: string): Promise<"finished" | "error"> {
  const mongoPort = process.env.DLQ_MONGO_PORT ?? "37017";
  await using agent = await Agent.create({
    apiKey,
    model: { id: "composer-2.5" },
    name: `dlq-${entry.id}-${entry.entity_name}`,
    local: {
      cwd: REPO_ROOT,
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

  const run = await agent.send(buildTriagePrompt(entry));
  console.log(`Started agent=${agent.agentId} run=${run.id} for dlq id=${entry.id}`);

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
  return result.status === "finished" ? "finished" : "error";
}

const LOCK_STALE_MS = 2 * 60 * 60 * 1000;

function isPidAlive(pid: number): boolean {
  try {
    process.kill(pid, 0);
    return true;
  } catch (err) {
    return (err as NodeJS.ErrnoException).code === "EPERM";
  }
}

/**
 * Single-file mutex on .dlq-agent.lock (no proper-lockfile, no .lock dir).
 * Returns a release fn, or null when another live agent holds the lock.
 */
async function acquireLock(): Promise<(() => Promise<void>) | null> {
  const write = async () => {
    await fs.writeFile(LOCK_PATH, JSON.stringify({ pid: process.pid, ts: Date.now() }), {
      flag: "wx",
    });
  };

  try {
    await write();
  } catch (err) {
    if ((err as NodeJS.ErrnoException).code !== "EEXIST") {
      throw err;
    }
    // Lock file exists — steal it only if the owner is dead or it is stale.
    let stale = true;
    try {
      const raw = await fs.readFile(LOCK_PATH, "utf8");
      const { pid, ts } = JSON.parse(raw) as { pid?: number; ts?: number };
      const fresh = typeof ts === "number" && Date.now() - ts < LOCK_STALE_MS;
      const ownerAlive = typeof pid === "number" && pid !== process.pid && isPidAlive(pid);
      stale = !(fresh && ownerAlive);
    } catch {
      stale = true; // unreadable / malformed → treat as stale
    }
    if (!stale) {
      return null;
    }
    await fs.rm(LOCK_PATH, { force: true });
    await write();
  }

  const release = async () => {
    try {
      await fs.rm(LOCK_PATH, { force: true });
    } catch {
      // ignore
    }
  };

  const onSignal = () => {
    try {
      rmSync(LOCK_PATH, { force: true });
    } finally {
      process.exit(1);
    }
  };
  process.once("SIGINT", onSignal);
  process.once("SIGTERM", onSignal);

  return release;
}

async function main(): Promise<void> {
  const apiKey = process.env.CURSOR_API_KEY;
  if (!apiKey) {
    console.error("CURSOR_API_KEY is required");
    process.exit(1);
  }

  const baseUrl = process.env.DLQ_API_BASE ?? "http://localhost:8080";

  const release = await acquireLock();
  if (!release) {
    console.log("Another DLQ agent is running (.dlq-agent.lock held); exiting.");
    process.exit(0);
  }

  let exitCode = 0;
  try {
    const entries = await fetchDlqEntries(baseUrl);
    const matching = entries.filter((e) => TARGET_EXCEPTION_CLASSES.has(e.exception_class));
    const skippedOther = entries.length - matching.length;
    if (skippedOther > 0) {
      console.log(`Skipped ${skippedOther} DLQ row(s) with non-target exception_class`);
    }
    if (matching.length === 0) {
      console.log("No matching unresolved DLQ entries; done.");
      return;
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
        if (err instanceof CursorAgentError) {
          console.error(
            `CursorAgentError for dlq id=${entry.id}: ${err.message} retryable=${err.isRetryable}`,
          );
          exitCode = 1;
          if (err.isRetryable) {
            console.error("Failure marked retryable; will retry on next cron tick.");
          }
        } else {
          console.error(`Unexpected error for dlq id=${entry.id}`, err);
          exitCode = 1;
        }
      }
    }
  } finally {
    if (release) {
      await release();
    }
  }

  process.exit(exitCode);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
