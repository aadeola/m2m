#!/usr/bin/env bash
# Load the bulk Postgres dataset for a backfill demo.
#
# Explicit opt-in: docker compose up alone keeps the tiny init seed.
# Idempotent: each run TRUNCATEs and rewrites the same deterministic rows
# (1k customers / 100 products / 10k orders / ~520k line items).
#
# Postgres PKs restart at 1 each run and the backfill reuses them as Mongo
# _id values, so this script also drops the Mongo customers/products/orders
# collections up front. That keeps the two stores in lockstep: Mongo only
# ever holds _id values that exist in the freshly seeded Postgres, with no
# orphaned documents from a previous, larger run.
#
# Contract tests share this Postgres — reset to the tiny seed before mvn test:
#   ./scripts/reset-postgres.sh

set -euo pipefail

# Mongo database from spring.data.mongodb.uri (…/mydb).
MONGO_DB="mydb"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if ! docker compose ps --status running 2>/dev/null | grep -q postgres; then
  echo "Postgres is not running. Start it with: docker compose up -d"
  exit 1
fi

if docker compose ps --status running 2>/dev/null | grep -q mongo; then
  echo "Clearing Mongo collections (customers/products/orders) in ${MONGO_DB}..."
  docker compose exec -T mongo mongosh --quiet --eval "
    const target = db.getSiblingDB('${MONGO_DB}');
    ['customers', 'products', 'orders'].forEach(c => target[c].drop());
  "
  # Dropping a collection also drops its $jsonSchema validator. mongo-init.js
  # only runs at container init, so re-apply the validators here — otherwise the
  # backfill re-creates the collections unvalidated and the poison order 100
  # silently passes instead of raising error 121 for the DLQ.
  echo "Re-applying Mongo schema validators (seeds/mongo-init.js)..."
  docker compose exec -T mongo mongosh --quiet < "$ROOT/seeds/mongo-init.js"
else
  echo "WARNING: Mongo is not running — skipping Mongo cleanup."
  echo "         Start it (docker compose up -d) and re-run to avoid orphaned"
  echo "         Mongo documents from a previous backfill."
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
echo "  - Mongo customers/products/orders were dropped; backfill will repopulate"
echo "    them with _id values matching these Postgres PKs (no divergence)."
echo "  - Run backfill with:"
echo "      mvn spring-boot:run -Dspring-boot.run.arguments=--backfill"
echo "  - Reset to tiny seed before contract tests:"
echo "      ./scripts/reset-postgres.sh"
