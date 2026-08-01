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
- `./scripts/verify-phase0.sh` — validate Phase 0 infrastructure
- `mvn spring-boot:run` — start the shim on :8080
- `mvn test` — run full test suite
- `mvn test -Dtest=*ContractTest` — run contract tests only
- `npm run coverage-check` — run coverage-check-runner.ts via Cursor SDK
