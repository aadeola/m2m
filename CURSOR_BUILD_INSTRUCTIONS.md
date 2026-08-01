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
- `src/main/java/.../OrderTransformer.java` — Phase 2b (backfill job)
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
2. A SQL seed script that initializes Postgres with customers, orders, line_items 
   tables. Include foreign key relationships and realistic sample data (at least 
   5 customers, 10 orders, 20 line items across them).
3. A Spring Boot Maven project scaffolded with spring-boot-starter-web, 
   spring-boot-starter-data-jpa, spring-boot-starter-data-mongodb, and JUnit 5. 
   Use Java 21. Set it up so mvn spring-boot:run works immediately.
4. Two or three tiny fake client services (plain REST clients, any language, 
   doesn't matter) that call the legacy API. Each should make 2-3 different HTTP 
   calls to different endpoints.
5. Create these files in the root:
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

Scan the /clients/* directories for every HTTP call made to the legacy API. 
Extract:
- Endpoint path (e.g. /orders/{id}, /customers, etc.)
- HTTP method (GET, POST, etc.)
- Which fields are used in the response
- Which service owns the call

Write an inventory.json file with this structure:
[
  {
    "endpoint": "/orders/{id}",
    "method": "GET",
    "owning_service": "Client Service A",
    "fields_used": ["order_id", "customer", "line_items", "status", "total_amount"]
  },
  ...
]

Generate at least 5-7 entries covering different clients and endpoints.
```

---

## Phase 2 — Shim Layer

**Prompt to Cursor (Plan Mode first, then Agent mode):**

```
Phase 2: Shim Layer - Use Plan Mode

Propose a plan for a Spring Boot shim service that:
1. Exposes the LEGACY /orders REST API shape (read the inventory.json for endpoints)
2. Internally routes per-record based on migrated_at:
   - migrated_at IS NULL → hit Postgres (JPA repository)
   - migrated_at IS NOT NULL → hit MongoDB (Spring Data MongoDB)
3. Translates request/response shapes to preserve the legacy contract exactly
4. Uses DTOs and Jackson annotations to map legacy field names
5. Embeds line_items and product data directly in the Order document (no $lookup, 
   no joins — embedding is the pattern)
6. Includes a SHARED OrderTransformer that both the write path and the backfill 
   job (Phase 2b) will use

Review the plan, then build:
- @RestController for the endpoints (one per inventory.json entry, at least /orders/{id} GET/POST)
- @Document model for Order (with embedded List<LineItem>)
- DTOs (OrderRequest, OrderResponse) that match the legacy shape
- JPA repository (for Postgres, unmigrated records)
- MongoRepository (for MongoDB, migrated records)
- OrderTransformer (shared logic for the embedded document shape)
- Service layer to handle routing based on migrated_at
- Make responses byte-for-byte identical to the legacy shape

The shim should run on :8080.
```

---

## Phase 2b — Backfill Job

**Prompt to Cursor (Agent mode):**

```
Phase 2b: Backfill Job

Build a Spring Boot CommandLineRunner or scheduled @Component that:
1. Pages through Postgres orders table by PK (ascending order, any order is fine)
2. For each batch of unmigrated orders (migrated_at IS NULL):
   - JOIN in line_items and products (relational reads)
   - Transform via the SHARED OrderTransformer (same one the shim uses)
   - Bulk upsert into MongoDB (upsert by _id to be idempotent)
   - Set migrated_at on the Postgres row once Mongo write succeeds
3. Track its own resumability checkpoint (last processed PK)
4. Batch size of 10-20 records is fine for the demo
5. Make it runnable as: mvn spring-boot:run (should migrate a batch automatically)

Use Spring Data MongoDB's MongoTemplate for bulk operations.
```

---

## Phase 3 — Contract Tests

**Prompt to Cursor (Agent mode):**

```
Phase 3: Contract Tests

For each entry in inventory.json, generate a JUnit 5 test using @SpringBootTest and 
REST Assured that:
1. Makes the same HTTP call to BOTH:
   - Legacy Postgres endpoint (simulate or use a mock of the old API)
   - Shim endpoint (the real running Spring Boot app)
2. Compares responses field-by-field using JSONAssert
3. Fails if any field differs
4. Include TWO CASES per endpoint:
   - Unmigrated record (migrated_at IS NULL, served from Postgres via shim)
   - Migrated record (migrated_at IS NOT NULL, served from Mongo via shim)
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
3. **Wait for Phase 0 to complete** — docker-compose.yml should exist, Spring project scaffolded, AGENTS.md/mcp.json/skills set up
4. **Phase 1:** Paste the Phase 1 prompt, run discovery agent
5. **Phase 2:** Switch to Plan Mode, paste the Phase 2 plan prompt, review the plan, approve it, then switch back to Agent mode and paste the Phase 2 build prompt
6. **Phase 2b:** Agent mode, paste the Phase 2b prompt
7. **Phase 3:** Agent mode, paste the Phase 3 prompt
8. **Phase 4:** Agent mode, paste the Phase 4 prompt (both skill and SDK script)

---

## Test as You Go

After each phase, validate:
- **Phase 0:** `docker-compose up -d` — Postgres and Mongo should start, Mongo should initialize replica set
- **Phase 1:** `cat inventory.json` — should list discovered endpoints
- **Phase 2:** `mvn spring-boot:run` — shim should start on :8080
- **Phase 2b:** Hit an endpoint on the shim, watch the backfill job run, then hit it again — should see `migrated_at` populating
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
│   │       │   └── OrderController.java
│   │       ├── service/
│   │       │   └── OrderService.java
│   │       ├── repository/
│   │       │   ├── OrderJpaRepository.java
│   │       │   └── OrderMongoRepository.java
│   │       ├── transform/
│   │       │   └── OrderTransformer.java
│   │       ├── model/
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
- **Do hand the plan.md** — Cursor will read it as specification per phase, but you're steering phase by phase with individual prompts, not asking Cursor to build everything at once

The plan is a checklist for you. The individual phase prompts are what you feed to Cursor's agents.
