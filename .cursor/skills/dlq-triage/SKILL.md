---
name: dlq-triage
description: >-
  Triage unresolved backfill DLQ rows for MongoSchemaValidationException or
  BulkOperationException: reproduce in an isolated Docker stack, fix Java,
  open a PR, and write a report. Locking, same-batch PR skip, and git worktree
  isolation are handled by dlq-agent.ts, not this skill.
---
# DLQ Triage

Use this playbook when fixing a single backfill DLQ entry (schema validation or
bulk write failure). The SDK runner (`npm run dlq`) skips batches that already
have an open PR, and creates an isolated `git worktree` (checked out on the fix
branch, cut from `origin/main`) before invoking you — you are already running
inside that worktree. Do not re-implement locking/PR-skip, and do not run
`git checkout`/`git switch`/`git worktree` yourself.

## Inputs

You will be given a DLQ row: `id`, `entity_name`, `start_pk`, `end_pk`,
`exception_class`, `message`.

Target exception classes:

- `com.migration.exception.MongoSchemaValidationException` — Mongo `$jsonSchema`
  rejection (`validationAction=error`, code 121). Usually orders embedding shape.
- `org.springframework.data.mongodb.BulkOperationException` — other bulk write
  failures (inspect message; may still mention validation).

## Safety

- Never write to production Postgres (`:5432`) or Mongo (`:27017`).
- Never set `backfill_dlq.resolved = true`.
- Never commit to `main`.
- Never merge the PR.
- You are already on branch `dlq-fix/<entity>-<startPk>-<endPk>` in an isolated
  worktree cut from `origin/main` — just commit/push there.
- Do NOT remove or weaken the `migrated_at` embed filter in
  `BackfillService.toOrderDocument` (`product.getMigratedAt() != null`). That
  filter is a real migration invariant, not the bug.
- Do NOT weaken Mongo `$jsonSchema` validators (`seeds/mongo-init.js`).
- If root cause is a bad Postgres trigger / poison seed (installed by
  `scripts/seed-bulk.sql`, mirrored into isolation by `dlq-seed-subset.sh`),
  fix the seed script — do not paper over it in Java.
- Tear down: `docker compose -p dlq-<id> -f docker-compose.dlq.yml down -v`

## Steps

1. **Classify** from `exception_class` + `message`. For schema failures, read
   `seeds/mongo-init.js` (orders validator) and the transform/embed path
   (`OrderTransformer`, `BackfillService.toOrderDocument`).
2. **Isolate**
   ```bash
   docker compose -p dlq-<id> -f docker-compose.dlq.yml up -d
   ```
   Ports: Postgres `15432`, Mongo `37017`.
3. **Seed subset** from prod into isolation:
   ```bash
   ./scripts/dlq-seed-subset.sh <entity> <start_pk> <end_pk> localhost 15432
   ```
4. **Reproduce** with env overrides only (never point at prod):
   ```bash
   SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:15432/migration \
   SPRING_DATA_MONGODB_URI=mongodb://localhost:37017/mydb \
   mvn -q spring-boot:run -Dspring-boot.run.arguments=--backfill
   ```
5. **Investigate root cause before fixing.** Trace the failure to its origin
   (e.g. missing embedded product → `migrated_at` is NULL → why did that
   product never migrate — constraint, trigger, upstream seed?). Fix the real
   root cause per the invariants above; do not just make the write succeed.
6. **Re-test** in isolation (repeat backfill and/or targeted `mvn test`).
7. **PR** (you're already on the fix branch in your isolated worktree):
   ```bash
   git add -A && git commit -m "..."
   git push -u origin HEAD
   gh pr create --title "..." --body "..."
   ```
8. **Report** write `reports/dlq/<id>.md` (root cause, repro, fix, evidence, PR URL).
9. **Teardown** `docker compose -p dlq-<id> -f docker-compose.dlq.yml down -v`

Prefer the MongoDB MCP (isolated URI `mongodb://host.docker.internal:37017/mydb`)
for inspecting documents.
