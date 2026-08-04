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
- Backfill DLQ (`backfill_dlq`): failed batches are caught (app does not crash),
  recorded with exception class/message, and that entity's migration halts for the run.
  Mongo schema validation failures (code 121) are wrapped as
  `MongoSchemaValidationException` before DLQ persistence.

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
- Admin DLQ API `GET /admin/backfill/dlq` is internal tooling (not a legacy client
  contract); do not add it to inventory.json / contract tests
- DLQ agent safety: never write to prod DBs from the triage agent, never set
  `resolved=true` from the agent, never commit to `main`, branch
  `dlq-fix/<entity>-<startPk>-<endPk>`, tear down `docker compose -p dlq-<id> … down -v`

## Commands
- `docker-compose up -d` — start Postgres + Mongo
- `./scripts/verify-phase0.sh` — validate Phase 0 infrastructure
- `./scripts/seed-bulk.sh` — load 5k customers / 100 products / ~50k orders / ~2.6M line items for a multi-minute backfill demo (destructive rewrite; clear Mongo separately if needed)
- `./scripts/reset-postgres.sh` — recreate Postgres and restore the tiny init seed (use before contract tests after a bulk seed)
- `mvn spring-boot:run` — start the shim on :8080
- `mvn spring-boot:run -Dspring-boot.run.arguments=--backfill` — run the backfill job only (explicit; does not run on normal startup)
- `mvn test` — run full test suite
- `mvn test -Dtest=*ContractTest` — run contract tests only
- `npm run coverage-check` — run coverage-check-runner.ts via Cursor SDK
- `npm run dlq` — poll `/admin/backfill/dlq` and triage matching rows via Cursor SDK
  (requires `CURSOR_API_KEY`, shim on :8080, `gh` auth). Cron every minute:
  `* * * * * cd /path/to/m2m && npm run dlq`
- Isolated DLQ stack: `docker compose -p dlq-<id> -f docker-compose.dlq.yml up -d`
  (Postgres host port **15432**, Mongo **37017**); seed with
  `./scripts/dlq-seed-subset.sh <entity> <start_pk> <end_pk> localhost 15432`

## DLQ agent concurrency
- Single-agent flock on `.dlq-agent.lock` — if held, the runner exits 0
- Processes only `MongoSchemaValidationException` and `BulkOperationException`
- Skips a batch if an open PR already exists for branch
  `dlq-fix/<entity>-<startPk>-<endPk>`; other open DLQ PRs do not block different batches
- Does not mark DLQ rows resolved (post-merge / successful backfill is separate)
