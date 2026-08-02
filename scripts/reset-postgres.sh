#!/usr/bin/env bash
# Recreate the Postgres container so docker-entrypoint re-runs the tiny
# seeds/postgres-seed.sql init (schema + ~10 orders). Use this after a bulk
# seed before running contract tests or Phase 0 verification.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "Recreating Postgres to restore the tiny init seed..."
docker compose stop postgres
docker compose rm -f postgres
docker compose up -d postgres

echo "Waiting for Postgres to become healthy..."
for _ in $(seq 1 60); do
  if docker compose exec -T postgres pg_isready -U postgres -d migration >/dev/null 2>&1; then
    echo "Postgres is ready with the tiny seed."
    docker compose exec -T postgres psql -U postgres -d migration -c \
      "SELECT 'customers' AS entity, COUNT(*) FROM customers
       UNION ALL SELECT 'products', COUNT(*) FROM products
       UNION ALL SELECT 'orders', COUNT(*) FROM orders
       UNION ALL SELECT 'line_items', COUNT(*) FROM line_items
       ORDER BY 1;"
    exit 0
  fi
  sleep 1
done

echo "Timed out waiting for Postgres after recreate."
exit 1
