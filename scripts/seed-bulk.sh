#!/usr/bin/env bash
# Load the bulk Postgres dataset for a multi-minute backfill demo.
#
# Explicit opt-in: docker compose up alone keeps the tiny init seed.
# Idempotent: each run TRUNCATEs and rewrites the same deterministic rows
# (5k customers / 100 products / 50k orders / ~2.6M line items).
#
# Does NOT clear Mongo. If a previous backfill left documents, clear them
# before re-demoing, e.g.:
#   docker compose exec -T mongo mongosh --quiet --eval \
#     'db.getSiblingDB("mydb").dropDatabase()'
# (Spring uses spring.data.mongodb.uri …/mydb)
#
# Contract tests share this Postgres — reset to the tiny seed before mvn test:
#   ./scripts/reset-postgres.sh

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if ! docker compose ps --status running 2>/dev/null | grep -q postgres; then
  echo "Postgres is not running. Start it with: docker compose up -d"
  exit 1
fi

echo "Loading bulk seed into m2m-postgres (this may take a few minutes)..."
docker compose exec -T postgres psql -U postgres -d migration -v ON_ERROR_STOP=1 \
  -f - < "$ROOT/scripts/seed-bulk.sql"

echo
echo "Bulk seed complete."
echo
echo "Counts:"
docker compose exec -T postgres psql -U postgres -d migration -c \
  "SELECT 'customers' AS entity, COUNT(*) FROM customers
   UNION ALL SELECT 'products', COUNT(*) FROM products
   UNION ALL SELECT 'orders', COUNT(*) FROM orders
   UNION ALL SELECT 'line_items', COUNT(*) FROM line_items
   ORDER BY 1;"

echo
echo "Reminders:"
echo "  - Clear Mongo before a clean backfill if prior documents exist."
echo "  - Run backfill with:"
echo "      mvn spring-boot:run -Dspring-boot.run.arguments=--backfill"
echo "  - Reset to tiny seed before contract tests:"
echo "      ./scripts/reset-postgres.sh"
