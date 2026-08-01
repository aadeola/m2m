# Cursor Build Instructions — Complete File List & Phase Prompts

## Files to Hand to Cursor

**Reference files (keep open during build, reference but don't require as input):**
- `mongo-migration-shim-plan.md` — read this yourself, use it to direct Cursor phase by phase
- `architecture-diagram.mermaid` — visual reference, shows what you're building
- `db-schema-diagram.mermaid` — visual reference, shows the data model

**Files Cursor will CREATE during the build (you start with nothing, these get generated):**
- `AGENTS.md` (root) — drop this in during Phase 0
- `.cursor/mcp.json` — drop this in during Phase 0
- `.cursor/skills/coverage-check/SKILL.md` — drop this in during Phase 0 or Phase 4
- `docker-compose.yml` — Phase 0
- SQL seed scripts — Phase 0
- `pom.xml` + Spring Boot project structure — Phase 0
- Sample client repos (fake services) — Phase 0
- `src/main/java/.../*.java` (shim controllers, repos, services, DTOs) — Phase 2
- `src/main/java/.../*Transformer.java` — shared by Phase 2 + Phase 2b
- `src/main/java/.../DataSourceResolver.java` — Phase 2
- `src/main/java/.../BackfillJob.java` — Phase 2b
- `src/test/java/.../contract/*Test.java` — Phase 3
- `coverage-check-runner.ts` — Phase 4

**Stretch goal file (separate, only if you have time after core is done):**
- `snowflake-analytics-stretch-goal.md` — read this yourself if building the stretch goal

---

## Phase 0 — Environment Setup

**Prompt to Cursor (Agent or Composer mode):**

```
Use the mongo-migration-shim-plan.md Phase 0 section as your specification.

Build:
1. A docker-compose.yml with postgres:16 and mongo:7 services. Mongo MUST run 
   in replica set mode (--replSet rs0, bind_ip_all). Include health checks and 
   port mappings (5432 for Postgres, 27017 for Mongo).
2. A SQL seed script that initializes Postgres with customers, products, orders, 
   and line_items tables. Include foreign key relationships and realistic sample 
   data (at least 5 customers, 10 products, 10 orders, 20 line items across them).
   Add migrated_at TIMESTAMP NULL to customers, products, and orders. line_items
   stay relational only; they do NOT become a standalone Mongo collection.
3. A Spring Boot Maven project scaffolded with spring-boot-starter-web, 
   spring-boot-starter-data-jpa, spring-boot-starter-data-mongodb, and JUnit 5. 
   Use Java 21. Set it up so mvn spring-boot:run works immediately.
4. Seed Mongo with a small set of already-migrated documents using the MongoDB 
   MCP server directly from Cursor. Create customers, products, and orders 
   collections. This is part of Phase 0 because it proves the MCP connection works
   before later phases depend on it.
5. Two or three tiny fake client services (plain REST clients, any language, 
   doesn't matter) that call the legacy API. Together they should cover orders,
   customers, and products. Add at least two product read call sites (for example
   GET /products/{id}) across the fake clients so Phase 1 discovers product usage.
   Do not keep this to only 2-3 total calls; create enough realistic call sites to
   support a 10-12 entry inventory in Phase 1.
6. Create these files in the root:
   - AGENTS.md (from the plan §5)
   - .cursor/mcp.json (from the plan §7) with MongoDB MCP server configured
   - .cursor/skills/coverage-check/SKILL.md (from the plan §6)

Don't build the shim logic yet. Just the infrastructure and context files.
```

---

## Phase 1 — Discovery Agent

**Prompt to Cursor (Agent mode):**

```
Phase 1: Discovery Agent

Scan the /clients/* directories for every HTTP call or SDK-style call made to the
legacy API.
Extract:
- Endpoint path (e.g. /orders/{id}, /customers, etc.)
- HTTP method (GET, POST, etc.)
- Which service owns the call
- Source file / call site location
- Which fields are used in the response
- Request body fields for write endpoints
- Query params used by the client
- Path params used by the client

Create ONE inventory entry per real client call site, even if multiple services hit
the same endpoint. The goal is call-site coverage, not just unique endpoint coverage.

Write an inventory.json file with this structure:
[
  {
    "endpoint": "/orders/{id}",
    "method": "GET",
    "owning_service": "Client Service A",
    "source_file": "clients/client-a/legacy_calls.sh",
    "path_params": ["id"],
    "query_params": [],
    "request_fields": [],
    "fields_used": ["order_id", "customer_id", "line_items", "status", "total_amount"]
  },
  ...
]

Generate at least 10-12 entries covering different clients and endpoints, including
product call sites. After generation, sanity-check that the inventory matches what
you actually put in the sample repos before moving on to Phase 2.
```

---

## Phase 2 — Shim Layer

**Prompt to Cursor (Plan Mode first):**

```
Phase 2: Shim Layer - Planning Pass

Read inventory.json and the Phase 2 section of mongo-migration-shim-plan.md.

Propose a plan for a Spring Boot shim service that:
1. Exposes the LEGACY API shape for the endpoints actually discovered in
   inventory.json, covering customers, products, and orders.
2. Uses three controller/service/repository areas:
   - CustomerController / CustomerService
   - ProductController / ProductService
   - OrderController / OrderService
3. Centralizes routing in ONE reusable datasource-resolution step, called as the
   first thing every read/write path does:
   - resolveDataSource(id)
   - if id is a Mongo ObjectId, route directly to Mongo with no DB lookup
   - if id is numeric, check Postgres migrated_at to determine whether Mongo or
     Postgres is authoritative
   - this is routing resolution, not business validation
   - do NOT scatter inline routing if-statements across services
   - do NOT treat resolution as startup-time or long-lived cached state; it must
     run per request because backfill changes authority record by record
4. For create flows, generate the new ObjectId first, then keep routing behavior
   consistent by using the same centralized resolution rule / component.
5. Translates request/response shapes to preserve the legacy contract exactly,
   using DTOs and Jackson annotations where field names differ.
6. Uses Mongo document design intentionally:
   - orders embed line_items and denormalized product data
   - orders embed a lightweight customer summary
   - customer-specific and product-specific endpoints still use their own
     entity-specific models/contracts
   - no $lookup-heavy read path for orders
7. Uses shared transformers as the only source of truth for migrated document
   shapes (OrderTransformer plus equivalents for customers/products if needed by
   the chosen design), reused by both live writes and Phase 2b backfill.
8. Explains how list/query endpoints behave during the migration window:
   - GET /orders
   - GET /orders?customer_id={customer_id}
   - GET /customers/{id}/orders
   Be explicit about whether results come from one store or a merged Postgres +
   Mongo view, and how the legacy response shape stays consistent.
9. Treats this routing logic as temporary scaffolding that can later collapse to
   always-Mongo once backfill is complete, while keeping the contract-translation
   layer intact.

Return the plan first. Do not implement yet.
```

**Prompt to Cursor (Agent mode after plan approval):**

```
Phase 2: Shim Layer - Build Pass

Implement the approved shim plan from the planning pass.

Build:
- Controllers/services/repositories for customers, products, and orders, matching
  the endpoints discovered in inventory.json
- A shared DataSource enum plus a single reusable DataSourceResolver (or
  equivalent component) that exposes resolveDataSource(id)
- Make every read/write path call resolveDataSource(id) before doing real work
- JPA repositories for legacy-origin records in Postgres
- Mongo repositories for target-state records in MongoDB
- DTOs that match the legacy JSON byte-for-byte
- Mongo models/documents for customers, products, and orders
- Order documents with embedded line_items, denormalized product data, and a
  lightweight embedded customer summary
- Shared transformer logic for migrated document shapes, reused by Phase 2 and 2b

Specific routing rules:
- ObjectId-shaped IDs always route to Mongo with no database query
- Numeric IDs check Postgres migrated_at and then route to Mongo or Postgres
- Postgres is the only source of truth for migrated_at
- Do not inline routing logic separately inside each service
- Do not cache datasource resolution across requests

New record creation:
- New records write to Mongo only
- Assign a fresh ObjectId
- Keep the routing rule centralized rather than creating a special scattered path

Minimum endpoint coverage:
- GET /orders/{id}
- GET /orders
- GET /orders?customer_id={customer_id}
- GET /orders/{id}/status
- POST /orders
- GET /customers
- GET /customers/{id}
- GET /customers/{id}/orders
- Product endpoints discovered in inventory.json

The shim should run on :8080. Make responses byte-for-byte identical to the
legacy shape.
```

---

## Phase 2b — Backfill Job

**Prompt to Cursor (Agent mode):**

```
Phase 2b: Backfill Job

Build a Spring Boot ApplicationRunner that runs only when --backfill is passed:
1. Adds migrated_at TIMESTAMP NULL to customers, products, and orders if Phase 0
   did not already do so. Postgres is the source of truth for migration status on
   legacy-origin records.
2. Runs in dependency order:
   - customers first
   - products second
   - orders last
3. For each entity, page through the Postgres table by PK (ascending order is fine;
   any convenient order is fine because completeness matters more than chronology).
4. For each batch of unmigrated rows:
   - Read the Postgres row(s)
   - For orders, JOIN in line_items and product details
   - Transform via the SHARED transformer for that entity (same transformer family
     the shim uses)
   - Bulk upsert into MongoDB using the original Postgres PK as the Mongo _id for
     legacy-origin records
   - Set migrated_at on the Postgres row once the Mongo write succeeds
5. Track a separate resumability checkpoint per entity (last processed PK). Do not
   conflate job progress with migrated_at.
6. Batch size of 10-20 records is fine for the demo
7. Make it runnable as:
   mvn spring-boot:run -Dspring-boot.run.arguments=--backfill
   Do NOT run backfill on normal startup — only when --backfill is passed explicitly.

Use Spring Data MongoDB's MongoTemplate for bulk operations. Query Mongo through
the MongoDB MCP server to verify documents landing in customers, products, and
orders collections.
```

---

## Phase 3 — Contract Tests

**Prompt to Cursor (Agent mode):**

```
Phase 3: Contract Tests

For each entry in inventory.json (across customers, products, and orders
endpoints), generate a JUnit 5 test using @SpringBootTest and REST Assured that:
1. Makes the same HTTP call to BOTH:
   - Legacy Postgres endpoint (simulate or use a mock of the old API)
   - Shim endpoint (the real running Spring Boot app)
2. Compares responses field-by-field using JSONAssert
3. Fails if any field differs
4. Include routing-specific cases for every endpoint where they apply:
   - Unmigrated record (migrated_at IS NULL, served from Postgres via shim)
   - Migrated record (migrated_at IS NOT NULL, served from Mongo via shim)
   - Brand-new ObjectId-keyed record created after migration start, served from
     Mongo with no migrated_at lookup
5. Use the MongoDB MCP server to pull a real sample document to seed test data

Save tests to src/test/java/.../contract/*Test.java

Make them all pass (mvn test should pass suite).
```

---

## Phase 4 — Coverage Check

**Prompt to Cursor (Agent mode for the skill, then Agent mode for the SDK script):**

```
Phase 4: Coverage Check

Step 1: Create the Skill

Create .cursor/skills/coverage-check/SKILL.md with the following content (from plan §6):
- Name: coverage-check
- Description: Verify every call site in inventory.json has a contract test
- Steps:
  1. Read inventory.json
  2. Scan src/test/java/.../contract for test files
  3. For each call site, check if a matching test exists
  4. Use MongoDB MCP server to pull real sample documents
  5. Generate stub tests for any gaps found
  6. Report which are covered, which are not

Step 2: Build the SDK Script

Create coverage-check-runner.ts that:
1. Imports Agent from @cursor/sdk
2. Creates an agent with:
   - apiKey from CURSOR_API_KEY env var
   - model: "composer-2"
   - local: { cwd: process.cwd() }
3. Prompts the agent to run the coverage-check skill
4. Streams events and returns result

This script is callable as: npm run coverage-check (or node coverage-check-runner.ts)
It's how the coverage check runs outside the Cursor IDE (e.g. in CI).
```

---

## How to Feed Phases to Cursor

1. **Start in Cursor IDE** with the repo root open
2. **Phase 0:** Open Agent/Composer mode, paste the Phase 0 prompt
3. **Wait for Phase 0 to complete** — docker-compose.yml should exist, Spring project scaffolded, AGENTS.md/mcp.json/skills set up, Mongo should be seeded, and fake clients should include order/customer/product call sites
4. **Phase 1:** Paste the Phase 1 prompt, run discovery agent
5. **Do not start Phase 2 until Phase 1 inventory is complete** — make sure inventory.json includes product call sites and reaches roughly 10-12 real call-site entries
6. **Phase 2:** Switch to Plan Mode, paste the Phase 2 planning prompt, review the plan, approve it, then switch back to Agent mode and paste the Phase 2 build prompt
7. **Phase 2b:** Agent mode, paste the Phase 2b prompt
8. **Phase 3:** Agent mode, paste the Phase 3 prompt
9. **Phase 4:** Agent mode, paste the Phase 4 prompt (both skill and SDK script)

---

## Test as You Go

After each phase, validate:
- **Phase 0:** `docker-compose up -d` — Postgres and Mongo should start, Mongo should initialize replica set, Mongo seed data should exist, and fake clients should include order/customer/product call sites
- **Phase 1:** inspect `inventory.json` — it should list 10-12 real call-site entries, including product call sites plus request/query/path metadata
- **Phase 2:** `mvn spring-boot:run` — shim should start on :8080 and the routing rule should live behind one reusable resolver
- **Phase 2b:** `mvn spring-boot:run -Dspring-boot.run.arguments=--backfill` — watch customers, products, then orders migrate; confirm `migrated_at` populates while Mongo collections fill in via MCP queries
- **Phase 3:** `mvn test` — all contract tests should pass
- **Phase 4:** `npm run coverage-check` — should scan and report on coverage

---

## File Structure After Build Complete

```
.
├── docker-compose.yml
├── pom.xml
├── AGENTS.md
├── .cursor/
│   ├── mcp.json
│   └── skills/
│       └── coverage-check/
│           └── SKILL.md
├── src/
│   ├── main/java/
│   │   └── com/migration/
│   │       ├── controller/
│   │       │   ├── CustomerController.java
│   │       │   ├── ProductController.java
│   │       │   └── OrderController.java
│   │       ├── service/
│   │       │   ├── CustomerService.java
│   │       │   ├── ProductService.java
│   │       │   └── OrderService.java
│   │       ├── routing/
│   │       │   └── DataSourceResolver.java
│   │       ├── repository/
│   │       │   ├── CustomerJpaRepository.java
│   │       │   ├── CustomerMongoRepository.java
│   │       │   ├── ProductJpaRepository.java
│   │       │   ├── ProductMongoRepository.java
│   │       │   ├── OrderJpaRepository.java
│   │       │   └── OrderMongoRepository.java
│   │       ├── transform/
│   │       │   ├── CustomerTransformer.java
│   │       │   ├── ProductTransformer.java
│   │       │   └── OrderTransformer.java
│   │       ├── model/
│   │       │   ├── Customer.java
│   │       │   ├── Product.java
│   │       │   ├── Order.java
│   │       │   ├── LineItem.java
│   │       │   └── dtos/
│   │       └── job/
│   │           └── BackfillJob.java
│   └── test/java/
│       └── com/migration/contract/
│           ├── GetOrderContractTest.java
│           ├── CreateOrderContractTest.java
│           └── ...
├── inventory.json
├── coverage-check-runner.ts
├── clients/
│   ├── client-a/
│   ├── client-b/
│   └── client-c/
└── seeds/
    └── postgres-seed.sql
```

---

## Stretch Goal (only if time permits)

Once core phases are complete:

**Prompt to Cursor:**
```
Stretch Goal: Snowflake Analytics

Read snowflake-analytics-stretch-goal.md and follow the setup steps:
1. Confirm Mongo is in replica set mode (rs.initiate() if needed)
2. Set up ngrok tunnel to expose local Mongo (or decide to use pre-loaded data)
3. In Snowflake: create OpenFlow deployment, install MongoDB connector
4. Configure connector to replicate orders collection
5. Wait for initial load to complete
6. Create the order_line_items view to flatten embedded arrays
7. Run the analytics queries and show results

This is optional and can be skipped if time is short.
```

---

## Note: Don't Mix Files

- **Do NOT hand the demo script to Cursor** — it's for YOU to read, not the agent
- **Do NOT hand the architecture diagram to Cursor** — it's a reference visual
- **Do hand `mongo-migration-shim-plan.md` to Cursor** — Cursor will read it as specification per phase, but you're steering phase by phase with individual prompts, not asking Cursor to build everything at once

The plan is a checklist for you. The individual phase prompts are what you feed to Cursor's agents.
