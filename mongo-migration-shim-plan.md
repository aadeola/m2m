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
   database is authoritative for a given record — Postgres or MongoDB — during
   the migration window. This covers **three entities**: `customers`, `products`,
   and `orders` (line items don't get their own migration — they only ever exist
   embedded inside an order document in the new model).
2. A **backfill job** that migrates existing Postgres data into MongoDB in the
   background, in parallel with live traffic, sharing the same transform logic
   the shim uses for new writes. Runs in dependency order: customers → products
   → orders, since orders embed denormalized copies of both.
3. **Contract tests** that prove the shim behaves identically to the old Postgres-backed
   API for every real client call site, across all three entities, regardless of
   which DB is actually serving a given record.
4. A **coverage check agent** that verifies every call site in `inventory.json`
   has a corresponding contract test — so nothing discovered by the discovery
   agent falls through without test coverage before cutover.

**How old vs. new records are distinguished:** by the shape of the ID itself, not
a lookup. Migrated legacy records keep their original Postgres integer PK as their
Mongo `_id` (so existing client-known IDs keep working). New records created after
migration starts get a Mongo-native `ObjectId`. The shim can tell which world a
record was born in just by looking at the ID format — no extra query needed. See
Phase 2 for the routing logic this enables.

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
   │  :8080)     │  CustomerController /
   │             │  ProductController /
   │             │  OrderController
   └──────┬──────┘
          │ routes by ID shape (numeric PK vs ObjectId),
          │ then migrated_at for numeric-PK records
          │
     ┌────┴─────┐
     ▼          ▼
┌─────────┐  ┌─────────┐        ┌────────────────────┐
│Postgres │  │ MongoDB │◄───────│  Backfill Job        │
│(legacy, │  │(target, │        │  (background, runs   │
│Docker)  │  │ Docker) │        │  customers → products│
│         │  │         │        │  → orders, in that    │
│customers,│ │customers,│       │  order — see Phase 2b)│
│products,│  │products, │       └─────────┬────────────┘
│orders   │  │orders    │                 │
│each has │  │migrated  │                 │
│migrated_│  │records   │                 │
│at column│  │keep      │                 │
│         │  │Postgres  │                 │
│         │  │PK as _id;│                 │
│         │  │new       │                 │
│         │  │records   │                 │
│         │  │get       │                 │
│         │  │ObjectId  │                 │
└────┬────┘  └────┬────┘                  │
     │            │                       │
     └─────►Shared Transformers◄──────────┘
      (CustomerTransformer, ProductTransformer,
       OrderTransformer — each used by BOTH the
       shim write path and the backfill job)
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
                     │                         │  across all three entities
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
| Migration status tracking | `migrated_at TIMESTAMP NULL` column on all three
  Postgres tables (`customers`, `products`, `orders`) — checked only for
  numeric-PK (legacy-origin) records. New records get a Mongo `ObjectId` as
  `_id`, so the ID format alone signals "always route to Mongo," no lookup
  needed (see Phase 2) |
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
- [ ] Seed script for Postgres: `customers`, `products`, `orders`, `line_items`
  tables with FK relationships — plain SQL init script mounted into the Postgres
  container. Note: `customers`, `products`, and `orders` each get their own
  MongoDB collection and their own `migrated_at` column; `line_items` does NOT
  get migrated as a standalone collection — it only ever exists embedded inside
  an order document in the new model
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
- [ ] Three Spring Boot `@RestController`s, one per entity, each exposing the
  **old** API shape for both reads and writes — legacy clients never see the
  Mongo document shape:
  - `CustomerController` (e.g. `/customers/{id}`)
  - `ProductController` (e.g. `/products/{id}`)
  - `OrderController` (e.g. `/orders/{id}`)
- [ ] `@Repository` layer split in two, per entity:
  - JPA repositories (`CustomerJpaRepository`, `ProductJpaRepository`,
    `OrderJpaRepository`) against Postgres, used for not-yet-migrated records
  - `MongoRepository`-based repositories against MongoDB, the real target
- [ ] **Routing logic — ID shape first, then `migrated_at` if needed:**
  1. Check the incoming ID's format:
     - **Numeric (legacy Postgres PK)** → this record predates the migration.
       Check its `migrated_at` column: `IS NOT NULL` → route to Mongo,
       `IS NULL` → route to Postgres (not backfilled yet)
     - **ObjectId (Mongo-native, 24-char hex)** → this record was created after
       migration started. Always route to Mongo, no `migrated_at` check needed
  2. This means routing is a two-tier check, but the second tier (`migrated_at`)
     only ever applies to legacy-origin records — new records skip it entirely
  3. This routing logic is temporary scaffolding: once backfill completes and
     enough time passes that legacy-PK lookups become rare, it can collapse to
     "always Mongo," but the *contract translation* below stays indefinitely
- [ ] **New record creation (POST):** always writes to Mongo ONLY — no Postgres
  write at all. Assign a fresh `ObjectId` as `_id`. This is what makes it a real
  migration rather than a permanent dual-write system: Postgres is frozen at the
  moment migration starts and only ever shrinks via backfill, never grows
- [ ] **Write-path transform:** an incoming legacy-shaped write (e.g. a nested
  order+line-items payload) must be transformed into the embedded Mongo document
  shape before it's persisted. This transform logic must be **shared with the
  backfill job** (see Phase 2b) — one `OrderTransformer` (and equivalents for
  customers/products), used by both request-time and batch paths, so there's a
  single source of truth for "what does a migrated record look like"
- [ ] Internally: translates relational query patterns → MongoDB document design,
  favoring **embedding over joins/`$lookup`** (joins are a Mongo anti-pattern —
  they defeat the point of a document model and hurt read performance)
  - `orders → line_items → products` join → a single `Order` document with an
    embedded `List<LineItem>`, each line item carrying denormalized product
    fields (name, price at time of order) it needs, not just a `product_id`
  - `orders → customers` → the order document embeds a lightweight customer
    summary (id, name, email) — full customer records still live in their own
    `customers` collection for customer-specific lookups
  - foreign keys → embedded subdocuments, not references, wherever the child
    data is always read together with the parent
  - transactions → Spring's `MongoTransactionManager` (requires Mongo replica
    set even locally — see note below) or documented limitation. Embedding
    reduces how often you need transactions in the first place, since a
    single-document write is already atomic
- [ ] DTOs (`OrderResponse`, `CustomerResponse`, `ProductResponse`) shaped to
  match the legacy JSON exactly, using Jackson annotations where field names differ
- [ ] Response shape must byte-for-byte match the legacy API's JSON shape, for
  all three entities

  **Local Mongo note:** transactions require Mongo to run as a (single-node)
  replica set, not the plain default container. Either start `mongo:7` with
  `--replSet rs0` and run `rs.initiate()` once, or explicitly punt transaction
  support and document it as a known limitation — either is a fine interview
  answer, just pick one on purpose.

### Phase 2b — Backfill Job (bulk migration)
- [ ] Add `migrated_at TIMESTAMP NULL` column to **all three** Postgres tables:
  `customers`, `products`, `orders`. This is the source of truth the shim checks
  (for legacy-PK records only) to route reads/writes
- [ ] Backfill runs in **dependency order**: customers first, then products, then
  orders — since the order transform embeds denormalized copies of both, and it's
  cleaner for `OrderTransformer` to read the already-migrated Mongo copies of
  customer/product rather than going back to Postgres for them
- [ ] For each entity, the job pages through the table **in any convenient order**
  (e.g. ascending PK) — order doesn't matter for correctness, only *completeness*
  does. For each unmigrated batch:
  1. Read the Postgres row(s) — for orders, join in `line_items` + `products`
  2. Transform via the **shared transformer** for that entity (same one the
     shim's write path uses) into the Mongo document shape
  3. Bulk upsert into MongoDB, **using the original Postgres PK as the Mongo
     `_id`** (not a generated ObjectId) — this is what lets already-known client
     IDs keep working after migration, and is also what makes the ID-shape
     routing check in Phase 2 possible: numeric `_id` = legacy-origin record
  4. Set `migrated_at` on the Postgres row once the Mongo write succeeds
- [ ] Job tracks its **own** resumability checkpoint (last processed PK) per
  entity — separate concern from `migrated_at`: the checkpoint is "where did
  the batch job get to," `migrated_at` is "is this specific record safe to
  read from Mongo"
- [ ] Batch size / throttle should be configurable (`application.properties`)
- [ ] Demo: seed Postgres with unmigrated rows across all three tables, run the
  backfill job live, then use the **MongoDB MCP server from inside Cursor** to
  query collections and show migrated documents landing in real time — no
  terminal switch needed

### Phase 3 — Contract Tests
- [ ] For each entry in `inventory.json` (across customers, products, and orders
  endpoints), generate a JUnit 5 test (using `@SpringBootTest` + REST Assured or
  `MockMvc`) that:
  1. Hits legacy Postgres-backed endpoint
  2. Hits shim (Mongo-backed) endpoint
  3. Diffs responses field-by-field via `JSONAssert`, fails on mismatch
- [ ] Use the **MongoDB MCP server** to pull a real sample document from the
  relevant collection to inform test generation — real field names and types,
  not guesses
- [ ] Add routing-specific test cases per entity: a legacy-PK record with
  `migrated_at IS NULL`, a legacy-PK record already migrated, and a brand-new
  ObjectId-keyed record — all three should be reachable through the same
  endpoint with identical response shapes
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
- Legacy: Postgres (`customers`, `products`, `orders`, `line_items` — see
  docker-compose.yml). `customers`, `products`, and `orders` each have a
  `migrated_at TIMESTAMP NULL` column. `line_items` is NOT separately migrated —
  it only ever exists embedded inside an order document in Mongo
- Target: MongoDB (denormalized, embedded documents) — collections: `customers`,
  `products`, `orders`
- Shim: Java 21 + Spring Boot 3 service (CustomerController, ProductController,
  OrderController), translates old REST API (reads + writes)
- **ID-based routing:** migrated legacy records keep their original Postgres PK
  as their Mongo `_id`; new records get a Mongo `ObjectId`. The shim checks ID
  shape first — numeric PK means check `migrated_at`, ObjectId means always Mongo
- **New records only ever write to Mongo** — no Postgres write on create.
  Postgres is frozen at migration start and only shrinks via backfill
- Backfill job: Spring `CommandLineRunner`/scheduled component, runs customers →
  products → orders in that order, migrates existing Postgres rows into Mongo
- Shared transformers (`CustomerTransformer`, `ProductTransformer`,
  `OrderTransformer`): used by BOTH the shim's write path and the backfill job —
  one source of truth per entity, don't duplicate this logic
- Package structure: controller / service / repository (jpa + mongo) / transform / dto

## Conventions
- All shim endpoints must preserve the original legacy response shape exactly
  (match field names via Jackson `@JsonProperty` where Java naming would differ)
- Contract tests live in src/test/java/.../contract, one per endpoint per entity,
  generated from inventory.json, and must cover unmigrated, migrated, and
  new-ObjectId record cases
- Never write directly to Mongo without going through the shim's service layer
  or the backfill job's shared transformer — no third path
- Discovery agent output (inventory.json) is the source of truth for "what needs a test"
- Document design favors **embedding over joins/`$lookup`** — `$lookup` is a Mongo
  anti-pattern for this data shape; only reach for it if you can justify why
  embedding genuinely doesn't fit (e.g. unbounded array growth)
- Backfill job's resumability checkpoint (last processed PK, per entity) is
  separate from `migrated_at` — don't conflate "where the batch job left off"
  with "is this record safe to read from Mongo"
- Never generate a numeric `_id` for a new Mongo record — always use `ObjectId`,
  since the ID format is how the shim distinguishes legacy-origin from
  Mongo-native records
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
   contract for every downstream client, and existing data (customers, products,
   orders) still has to move without stopping the business.
2. **Discovery agent (3 min):** run it live against sample client repos, show
   `inventory.json` covering all three entities.
3. **Explore with MCP (2 min):** use MongoDB MCP server inside Cursor to query
   local Mongo collections — show the agent reading live documents, no terminal
   switch needed.
4. **Shim in action (3 min):** hit an order endpoint, show identical response
   shape, walk through the relational join collapsing into a single embedded
   document (order embedding line items + a customer summary).
5. **Backfill in action (3 min):** run the backfill job (customers → products →
   orders), use MCP to query collections and show migrated documents landing in
   real time, each keeping its original Postgres PK as its Mongo `_id`.
6. **New record + ID routing (2 min):** create a new order via the shim, show
   it gets an `ObjectId`, never touches Postgres. Then hit both a legacy-PK
   record and the new ObjectId record through the same endpoint — identical
   response shape either way, different routing underneath.
7. **Contract tests (3 min):** run the suite green, break the shim, show a test
   catch it.
8. **Coverage check (2 min):** add a new call site to a client repo, rerun the
   coverage-check skill, show it flag the uncovered endpoint.
9. **Cursor SDK moment (~1 min):** run `npm run coverage-check` — same skill,
   callable from CI without opening Cursor.
10. **Live extension (interviewer prompt, ~5 min held in reserve)**

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
- "Why migrate customers and products before orders?" — because orders embed
  denormalized copies of both, so it's cleaner for the order transform to read
  already-migrated Mongo data instead of going back to Postgres twice
- "What happens if two clients create records with colliding IDs?" — good chance
  to explain why ObjectId was chosen for new records specifically: it can't
  collide with a Postgres sequence value or another ObjectId, no coordination needed
