# Project Plan: Legacy RDBMS → MongoDB Migration Shim

**Cursor Field Engineering Challenge — Problem Space A (Legacy Application Modernization)**

## 1. The Story

A business-critical service currently runs on Postgres. The org is migrating to MongoDB
for scalability/flexibility reasons. Because the data model changes from relational
(joins, foreign keys, transactions) to document-based (denormalized, nested), the
**API contract also has to change** — clients can't just point at a new connection
string. This project builds:

1. A **virtualization/shim layer** that lets old clients keep working against the
   legacy API shape (both reads AND writes) while data is routed to whichever
   database is authoritative for that record — Postgres or MongoDB — during the
   migration window.
2. A **backfill job** that migrates existing Postgres data into MongoDB in the
   background, in parallel with live traffic, sharing the same transform logic
   the shim uses for new writes.
3. **Contract tests** that prove the shim behaves identically to the old Postgres-backed
   API for every real client call site, regardless of which DB is actually serving
   a given record.
4. A **coverage check agent** that verifies every call site in `inventory.json`
   has a corresponding contract test — so nothing discovered by the discovery
   agent falls through without test coverage before cutover.

**Punted (mention as future work, don't build):** CDC-based migration (WAL streaming),
per-record write locking during promotion, auth/authz translation, connection
pooling at scale.

---

## 2. Architecture

```
                    ┌─────────────────────┐
   Client services  │  Discovery Agent     │  scans repos for call sites
   (fake sample      │  (Cursor agent)     │  against legacy REST API
   repos, 2-3)       └──────────┬───────────┘
         │                      │ inventory.json
         ▼                      ▼
   ┌─────────────┐      ┌──────────────────────┐
   │  Shim API   │◄─────│  Shim Generation      │
   │  (Java 21 + │      │  Agent (Cursor agent) │
   │  Spring     │      └──────────────────────┘
   │  Boot,      │
   │  :8080)     │
   └──────┬──────┘
          │ checks migrated_at, routes read/write
          │
     ┌────┴─────┐
     ▼          ▼
┌─────────┐  ┌─────────┐        ┌──────────────────┐
│Postgres │  │ MongoDB │◄───────│  Backfill Job      │
│(legacy, │  │(target, │        │  (background,      │
│Docker)  │  │ Docker) │        │  paginates Postgres│
│         │  │         │        │  by PK, any order — │
│has new  │  │         │        │  see Phase 2b)          │
│migrated_│  │         │        └─────────┬──────────┘
│at column│  │         │                  │
└────┬────┘  └────┬────┘                  │
     │            │                       │
     └─────►Shared OrderTransformer◄───────┘
          (used by both shim write path
           and backfill job — one source
           of truth for the embedded shape)
                                 │
                                 ▼
                     ┌───────────────────────┐
                     │  MongoDB MCP Server     │  database operations throughout:
                     │                         │  query collections, insert seed
                     │                         │  data, verify backfill, inspect
                     │                         │  documents — all from inside Cursor
                     └───────────┬────────────┘
                                 │
                     ┌───────────▼────────────┐
                     │  Contract Test Agent    │  diffs old vs new responses
                     └───────────┬────────────┘
                                 │
                     ┌───────────▼────────────┐
                     │  Coverage Check Agent   │  verifies every inventory.json
                     │  (SKILL.md invoked)     │  call site has a contract test
                     └────────────────────────┘
```

---

## 3. Tech Stack (all local, zero hosting cost)

| Component | Choice |
|---|---|
| Legacy DB | `postgres:16` via Docker |
| Target DB | `mongo:7` via Docker |
| Shim service | **Java 21 + Spring Boot 3** (Spring Web, port 8080) |
| Postgres client | Spring Data JPA (Hibernate) |
| Mongo client | Spring Data MongoDB (`MongoTemplate` for aggregation pipelines) |
| Build tool | Maven (or Gradle — Maven assumed below) |
| Contract tests | JUnit 5 + Spring Boot Test + REST Assured |
| Backfill job | Spring Boot `CommandLineRunner` or a scheduled `@Component`
  (simplest for a demo — no separate job runner needed) |
| Migration status tracking | `migrated_at TIMESTAMP NULL` column added to
  Postgres `orders` (see Phase 2b) |
| MCP | Official MongoDB MCP server (Docker image) — used throughout all
  stages as the agent's direct database access tool: querying collections,
  inserting seed/test data, verifying backfill output, inspecting documents,
  all from inside Cursor without switching to a terminal |
| Orchestration | Cursor agents (Composer/Agent mode) driving each stage |
| SDK script (`drift-check-runner.ts`) | Node.js + TypeScript — intentionally separate from the
  Java backend; it just needs to call `@cursor/sdk`, so no reason to run it inside the JVM |

---

## 4. Build Phases (feed this section to Cursor directly, phase by phase)

### Phase 0 — Environment Setup
- [ ] `docker-compose.yml` with `postgres` and `mongo` services
- [ ] Seed script for Postgres: `customers`, `orders`, `line_items` tables with FK
  relationships — plain SQL init script mounted into the Postgres container
- [ ] Seed Mongo with a small set of already-migrated documents using the **MongoDB
  MCP server** directly from Cursor — no terminal/mongosh needed. This also
  validates the MCP connection is working before you need it in later stages
- [ ] Spring Boot project scaffolded via Spring Initializr: `spring-boot-starter-web`,
  `spring-boot-starter-data-jpa`, `spring-boot-starter-data-mongodb`
- [ ] Sample client repos: 2-3 tiny fake services that call the legacy REST API
  (gives the discovery agent something real to scan)

### Phase 1 — Discovery Agent
- [ ] Cursor agent scans `/clients/*` for HTTP calls / SDK usage against the legacy API
- [ ] Outputs `inventory.json`: endpoint, fields used, owning service
- [ ] Manual check: does inventory match what you actually put in the sample repos?

### Phase 2 — Shim Layer
- [ ] Spring Boot `@RestController` exposing the **old** API shape (e.g. `/orders/{id}`)
  for both reads and writes — legacy clients never see the Mongo document shape
- [ ] `@Repository` layer split in two:
  - JPA repository (`OrderRepository extends JpaRepository`) against Postgres,
    used for not-yet-migrated records and for contract-test comparison
  - `MongoRepository`-based repository against MongoDB, the real target
- [ ] **Routing logic:** on every read/write, check the record's `migrated_at`
  column (see Phase 2b) —
  - `migrated_at IS NOT NULL` → route to Mongo
  - `migrated_at IS NULL` → route to Postgres (record hasn't been backfilled yet)
  - This routing is temporary scaffolding: once backfill completes, it collapses
    to "always Mongo" and can be deleted, but the *contract translation* below
    stays indefinitely, since that's the actual point of the shim
- [ ] **Write-path transform:** an incoming legacy-shaped write (e.g. a nested
  order+line-items payload) must be transformed into the embedded Mongo document
  shape before it's persisted. This transform logic must be **shared with the
  backfill job** (see Phase 2b) — one `OrderTransformer`, two callers (request-time
  and batch), so there's a single source of truth for "what does a migrated
  order look like"
- [ ] Internally: translates relational query patterns → MongoDB document design,
  favoring **embedding over joins/`$lookup`** (joins are a Mongo anti-pattern —
  they defeat the point of a document model and hurt read performance)
  - `orders → line_items → products` join → a single `Order` document with an
    embedded `List<LineItem>`, each line item carrying denormalized product
    fields (name, price at time of order) it needs, not just a `product_id`
  - foreign keys → embedded subdocuments, not references, wherever the child
    data is always read together with the parent (which is true here: you never
    fetch a line item without its order)
  - transactions → Spring's `MongoTransactionManager` (requires Mongo replica
    set even locally — see note below) or documented limitation. Embedding
    reduces how often you need transactions in the first place, since a
    single-document write is already atomic
- [ ] DTOs (`OrderResponse`, etc.) shaped to match the legacy JSON exactly, using
  Jackson annotations if field names need to differ from the Java field names
- [ ] Response shape must byte-for-byte match the legacy API's JSON shape

  **Local Mongo note:** transactions require Mongo to run as a (single-node)
  replica set, not the plain default container. Either start `mongo:7` with
  `--replSet rs0` and run `rs.initiate()` once, or explicitly punt transaction
  support and document it as a known limitation — either is a fine interview
  answer, just pick one on purpose.

### Phase 2b — Backfill Job (bulk migration)
- [ ] Add `migrated_at TIMESTAMP NULL` column to Postgres `orders` table. This is
  the single source of truth the shim checks to route reads/writes — it sidesteps
  needing a chronological timestamp or ordered PKs, since it's bookkeeping you
  control, not something inferred from existing data
- [ ] Backfill job (Spring `CommandLineRunner` or scheduled `@Component`) pages
  through `orders` **in any convenient order** (e.g. ascending PK) — order
  doesn't matter for correctness, only *completeness* does. For each unmigrated
  batch:
  1. Join in `line_items` + `products` (still relational at this point)
  2. Transform via the **shared `OrderTransformer`** (same one the shim's write
     path uses) into the embedded Mongo document shape
  3. Bulk upsert into MongoDB, keyed by the original Postgres `order_id`, so a
     crashed/restarted job doesn't duplicate records
  4. Set `migrated_at` on the Postgres row once the Mongo write succeeds
- [ ] Job tracks its **own** resumability checkpoint (last processed PK) —
  separate concern from `migrated_at`
- [ ] Batch size / throttle should be configurable (`application.properties`)
- [ ] Demo: seed Postgres with unmigrated rows, run the backfill job live, then
  use the **MongoDB MCP server from inside Cursor** to query the collection and
  show migrated documents landing in real time — no terminal switch needed

### Phase 3 — Contract Tests
- [ ] For each entry in `inventory.json`, generate a JUnit 5 test (using
  `@SpringBootTest` + REST Assured or `MockMvc`) that:
  1. Hits legacy Postgres-backed endpoint
  2. Hits shim (Mongo-backed) endpoint
  3. Diffs responses field-by-field via `JSONAssert`, fails on mismatch
- [ ] Use the **MongoDB MCP server** to pull a real sample document from the
  collection to inform test generation — the agent uses actual field names and
  types from live data rather than guessing at the shape
- [ ] Add routing-specific test cases: same contract test run against an
  unmigrated record (`migrated_at IS NULL`) and a migrated record — proving
  identical response regardless of which DB is authoritative
- [ ] Demo: show a passing suite (`mvn test`), then intentionally break the shim,
  show a test catch it

### Phase 4 — Coverage Check
- [ ] Skill (`coverage-check/SKILL.md`) invoked when the discovery agent finds
  new call sites or before any cutover decision
- [ ] Agent reads `inventory.json` and scans `src/test/java/.../contract` —
  for each call site in the inventory, checks whether a corresponding contract
  test file exists
- [ ] Reports any uncovered call sites: "these endpoints have no contract test,
  do not cut over until they do"
- [ ] Use the **MongoDB MCP server** to pull a sample document for any uncovered
  call site, so the agent can generate a stub contract test with real field names
  from live data rather than placeholders
- [ ] `coverage-check-runner.ts` (see §6b) — calls the **Cursor SDK** to run
  this same skill outside the editor (CI/cron)

---

## 5. AGENTS.md (place in project root)

```markdown
# Project: RDBMS → MongoDB Migration Shim

## Architecture
- Legacy: Postgres (customers, orders, line_items — see docker-compose.yml)
  - `orders` has a `migrated_at TIMESTAMP NULL` column used for migration routing
- Target: MongoDB (denormalized, embedded documents)
- Shim: Java 21 + Spring Boot 3 service, translates old REST API (reads + writes)
  → routes per-record to Postgres or Mongo based on `migrated_at`
- Backfill job: Spring `CommandLineRunner`/scheduled component, migrates existing
  Postgres rows into Mongo in the background, in any PK order
- Shared `OrderTransformer`: used by BOTH the shim's write path and the backfill
  job — one source of truth for the embedded document shape, don't duplicate it
- Package structure: controller / service / repository (jpa + mongo) / transform / dto

## Conventions
- All shim endpoints must preserve the original legacy response shape exactly
  (match field names via Jackson `@JsonProperty` where Java naming would differ)
- Contract tests live in src/test/java/.../contract, one per endpoint, generated
  from inventory.json, and must cover both migrated and unmigrated records
- Never write directly to Mongo without going through the shim's service layer
  or the backfill job's shared transformer — no third path
- Discovery agent output (inventory.json) is the source of truth for "what needs a test"
- Document design favors **embedding over joins/`$lookup`** — `$lookup` is a Mongo
  anti-pattern for this data shape; only reach for it if you can justify why
  embedding genuinely doesn't fit (e.g. unbounded array growth)
- Backfill job's resumability checkpoint (last processed PK) is separate from
  `migrated_at` — don't conflate "where the batch job left off" with "is this
  record safe to read from Mongo"
- **Use the MongoDB MCP server for all database operations from inside Cursor**:
  querying collections, inserting seed/test data, verifying backfill output,
  inspecting documents for test generation — avoid switching to a terminal for
  anything Mongo-related that the MCP can do

## Commands
- `docker-compose up -d` — start Postgres + Mongo
- `mvn spring-boot:run` — start the shim on :8080
- `mvn test` — run full test suite
- `mvn test -Dtest=*ContractTest` — run contract tests only
- `npm run coverage-check` — run coverage-check-runner.ts via Cursor SDK
```

---

## 6. Skill: Coverage Check (`.cursor/skills/coverage-check/SKILL.md`)

```markdown
---
name: coverage-check
description: Verify every call site in inventory.json has a corresponding contract
  test. Use before any cutover decision, or when the discovery agent finds new
  call sites.
---
# Coverage Check

1. Read inventory.json — the list of every client call site the discovery agent found.
2. Scan src/test/java/.../contract for existing contract test files.
3. For each call site in inventory.json, check whether a corresponding contract
   test exists (match by endpoint path and HTTP method).
4. For any uncovered call site, use the MongoDB MCP server to pull a real sample
   document from the relevant collection — use actual field names and types to
   generate a stub contract test, not placeholders.
5. Report: which call sites are covered, which are not, and output stub tests
   for any gaps found.
```

---

## 6b. Cursor SDK Script (`coverage-check-runner.ts`)

The challenge requires the demo to *use the Cursor SDK*. Everything else in this
project is you working live in the Cursor IDE (Ask mode, Plan mode, Rules, Skills,
MCP, model switching) — this one file calls `@cursor/sdk` directly so the
coverage-check skill can also run **outside the editor** (e.g. as a CI gate before
any cutover, or on a schedule to catch newly discovered call sites without test
coverage).

Note: the Cursor SDK is TypeScript-only, so this script stays Node/TS even though
the shim itself is Java/Spring — that's fine and worth saying explicitly if asked:
*"This is tooling around the project, not part of the application backend."*

```typescript
// coverage-check-runner.ts — run via `npm run coverage-check`, or as a CI gate
import { Agent } from "@cursor/sdk";

async function runCoverageCheck() {
  const agent = await Agent.create({
    apiKey: process.env.CURSOR_API_KEY!,
    model: { id: "composer-2" },
    local: { cwd: process.cwd() },
  });

  const run = await agent.prompt(
    "Run the coverage-check skill: verify every call site in inventory.json " +
    "has a corresponding contract test. Use the MongoDB MCP server to pull " +
    "sample documents for any gaps found and generate stub tests."
  );

  for await (const event of run.stream()) {
    console.log(event);
  }

  return run.result();
}

runCoverageCheck();
```

Note: confirm exact field names (`local`, `model.id`, `run.stream()`) against the
current SDK docs at cursor.com/docs/sdk/typescript before building — it's in
public beta and the API surface may shift.

---

## 7. MongoDB MCP Config (`.cursor/mcp.json` or Cursor MCP settings)

```json
{
  "mcpServers": {
    "MongoDB": {
      "command": "docker",
      "args": [
        "run", "--rm", "-i",
        "-e", "MDB_MCP_CONNECTION_STRING=mongodb://host.docker.internal:27017/mydb",
        "mongodb/mongodb-mcp-server:latest"
      ]
    }
  }
}
```
Enable write mode — the MCP server is used throughout the workflow for both reads
(querying collections, inspecting documents) and writes (seeding test data, verifying
backfill output). Write mode is required for seed/test operations during Explore and
the backfill verification stage.

---

## 8. Live Demo Script (~20 min)

1. **Problem framing (2 min):** legacy RDBMS → Mongo migration breaks the API
   contract for every downstream client, and existing data still has to move
   without stopping the business.
2. **Discovery agent (3 min):** run it live against sample client repos, show
   `inventory.json`.
3. **Explore with MCP (2 min):** use MongoDB MCP server inside Cursor to query
   the local Mongo collection — show the agent reading live documents, no
   terminal switch needed.
4. **Shim in action (3 min):** hit an endpoint, show identical response shape,
   walk through the relational join collapsing into a single embedded document.
5. **Backfill in action (3 min):** run the backfill job, then use MCP inside
   Cursor to query the collection and show migrated documents landing in real
   time. Hit the same endpoint before and after — same response, different DB.
6. **Contract tests (3 min):** run the suite green, break the shim, show a test
   catch it. Mention MCP was used to pull real documents to seed the test data.
7. **Coverage check (2 min):** add a new call site to a client repo, rerun the
   coverage-check skill, show it flag the uncovered endpoint. Agent uses MCP to
   pull a sample document and generate a stub test with real field names.
8. **Cursor SDK moment (~1 min):** run `npm run coverage-check` — same skill,
   callable from CI without opening Cursor. That's the SDK.
9. **Live extension (interviewer prompt, ~5 min held in reserve)**

---

## 9. Likely Interviewer Prompts to Prep For
- "Add a new client call site — show drift gets flagged."
- "Make the shim handle a transaction pattern that doesn't exist in Mongo."
- "Two services disagree on the old API's behavior — how does your system handle it?"
- "What changes here if this were 200 services instead of 3?"
- "What happens if a record is updated while the backfill job is mid-migration
  on that same record?" (good answer: routing checks `migrated_at` at request
  time, so a write either lands in Postgres pre-migration or Mongo post-migration
  — worth discussing the narrow race window if the backfill job is between
  reading and setting the flag)
- "How would you throttle the backfill so it doesn't overload production Postgres?"
- "Why not just use timestamps or ID ranges to track migration progress?" — good
  chance to explain why `migrated_at` (explicit state) beats inferring status
  from data you don't fully control
