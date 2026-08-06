#!/usr/bin/env bash
# Copy a targeted PK-range subset (+ FK closure) from prod Postgres into an
# isolated DLQ Postgres. Leaves Mongo empty so --backfill re-drives the slice.
#
# Usage:
#   ./scripts/dlq-seed-subset.sh <entity_name> <start_pk> <end_pk> [target_host] [target_port]
#
# Defaults: target_host=localhost target_port=15432
# Source: docker container m2m-postgres (prod compose).

set -euo pipefail

ENTITY="${1:?entity_name required (customers|products|orders)}"
START_PK="${2:?start_pk required}"
END_PK="${3:?end_pk required}"
TARGET_HOST="${4:-localhost}"
TARGET_PORT="${5:-15432}"

SOURCE_CONTAINER="${DLQ_SOURCE_PG_CONTAINER:-m2m-postgres}"
PGUSER="${POSTGRES_USER:-postgres}"
PGDATABASE="${POSTGRES_DB:-migration}"
export PGPASSWORD="${POSTGRES_PASSWORD:-postgres}"

if ! docker ps --format '{{.Names}}' | grep -qx "$SOURCE_CONTAINER"; then
  echo "Source Postgres container '$SOURCE_CONTAINER' is not running" >&2
  exit 1
fi

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

# Prefer host psql; fall back to docker exec into the container publishing TARGET_PORT.
resolve_target_container() {
  docker ps --filter "publish=${TARGET_PORT}" --format '{{.Names}}' | head -n 1
}

target_psql() {
  if command -v psql >/dev/null 2>&1; then
    psql -h "$TARGET_HOST" -p "$TARGET_PORT" -U "$PGUSER" -d "$PGDATABASE" -v ON_ERROR_STOP=1 "$@"
    return
  fi
  local container
  container="$(resolve_target_container)"
  if [[ -z "$container" ]]; then
    echo "psql not found on PATH and no Docker container is publishing port ${TARGET_PORT}" >&2
    exit 1
  fi
  docker exec -i -e PGPASSWORD="$PGPASSWORD" "$container" \
    psql -U "$PGUSER" -d "$PGDATABASE" -v ON_ERROR_STOP=1 "$@"
}

source_psql() {
  docker exec -i "$SOURCE_CONTAINER" psql -U "$PGUSER" -d "$PGDATABASE" -v ON_ERROR_STOP=1 "$@"
}

# Mirror every user-defined trigger (+ the function it calls) from prod into
# isolation, generically via catalog introspection — NOT hardcoded to any one
# trigger name. Without this, a backfill that only fails in prod because of a
# prod-only trigger (e.g. a poison-data guard) would silently succeed here,
# and "reproduce in isolation" would reproduce nothing.
copy_triggers_and_functions() {
  echo "Mirroring user-defined trigger functions and triggers from ${SOURCE_CONTAINER} into isolation..."

  local funcs
  funcs="$(source_psql -Atq -c "
    SELECT string_agg(pg_get_functiondef(p.oid), E';\n') || ';'
    FROM pg_proc p
    JOIN pg_namespace n ON p.pronamespace = n.oid
    WHERE p.oid IN (SELECT DISTINCT tgfoid FROM pg_trigger WHERE NOT tgisinternal)
      AND n.nspname = 'public'
  ")"
  if [[ -n "${funcs// }" && "$funcs" != ";" ]]; then
    target_psql -c "$funcs"
  fi

  local triggers
  triggers="$(source_psql -Atq -c "
    SELECT string_agg(
      'DROP TRIGGER IF EXISTS ' || t.tgname || ' ON ' || c.relname || E';\n'
      || pg_get_triggerdef(t.oid) || ';',
      E'\n'
    )
    FROM pg_trigger t
    JOIN pg_class c ON t.tgrelid = c.oid
    JOIN pg_namespace n ON c.relnamespace = n.oid
    WHERE n.nspname = 'public' AND NOT t.tgisinternal
  ")"
  if [[ -n "${triggers// }" ]]; then
    target_psql -c "$triggers"
  fi
}

dump_query() {
  local sql="$1"
  local outfile="$2"
  docker exec -i "$SOURCE_CONTAINER" \
    psql -U "$PGUSER" -d "$PGDATABASE" -v ON_ERROR_STOP=1 \
    -c "\COPY ($sql) TO STDOUT WITH CSV HEADER" > "$outfile"
}

restore_csv() {
  local table="$1"
  local infile="$2"
  local columns="$3"
  if [[ ! -s "$infile" ]] || [[ "$(wc -l < "$infile")" -le 1 ]]; then
    echo "No rows for $table — skipping"
    return 0
  fi
  target_psql -c "\COPY $table ($columns) FROM STDIN WITH CSV HEADER" < "$infile"
}

echo "Seeding isolated Postgres at ${TARGET_HOST}:${TARGET_PORT} with ${ENTITY} [${START_PK}..${END_PK}]"

copy_triggers_and_functions

case "$ENTITY" in
  customers)
    dump_query \
      "SELECT customer_id, first_name, last_name, account_number, phone_number, email, created_at, migrated_at
       FROM customers WHERE customer_id BETWEEN ${START_PK} AND ${END_PK} ORDER BY customer_id" \
      "$tmpdir/customers.csv"
    restore_csv customers "$tmpdir/customers.csv" \
      "customer_id, first_name, last_name, account_number, phone_number, email, created_at, migrated_at"
    target_psql \
      -c "SELECT setval(pg_get_serial_sequence('customers','customer_id'), COALESCE((SELECT MAX(customer_id) FROM customers), 1))"
    ;;
  products)
    dump_query \
      "SELECT product_id, name, sku, price, migrated_at
       FROM products WHERE product_id BETWEEN ${START_PK} AND ${END_PK} ORDER BY product_id" \
      "$tmpdir/products.csv"
    restore_csv products "$tmpdir/products.csv" \
      "product_id, name, sku, price, migrated_at"
    target_psql \
      -c "SELECT setval(pg_get_serial_sequence('products','product_id'), COALESCE((SELECT MAX(product_id) FROM products), 1))"
    ;;
  orders)
    dump_query \
      "SELECT DISTINCT c.customer_id, c.first_name, c.last_name, c.account_number, c.phone_number, c.email, c.created_at, c.migrated_at
       FROM customers c
       JOIN orders o ON o.customer_id = c.customer_id
       WHERE o.order_id BETWEEN ${START_PK} AND ${END_PK}
       ORDER BY c.customer_id" \
      "$tmpdir/customers.csv"
    dump_query \
      "SELECT DISTINCT p.product_id, p.name, p.sku, p.price, p.migrated_at
       FROM products p
       JOIN line_items li ON li.product_id = p.product_id
       JOIN orders o ON o.order_id = li.order_id
       WHERE o.order_id BETWEEN ${START_PK} AND ${END_PK}
       ORDER BY p.product_id" \
      "$tmpdir/products.csv"
    dump_query \
      "SELECT order_id, customer_id, order_date, status, total_amount, migrated_at
       FROM orders WHERE order_id BETWEEN ${START_PK} AND ${END_PK} ORDER BY order_id" \
      "$tmpdir/orders.csv"
    dump_query \
      "SELECT li.line_item_id, li.order_id, li.product_id, li.quantity, li.unit_price
       FROM line_items li
       WHERE li.order_id BETWEEN ${START_PK} AND ${END_PK}
       ORDER BY li.line_item_id" \
      "$tmpdir/line_items.csv"

    restore_csv customers "$tmpdir/customers.csv" \
      "customer_id, first_name, last_name, account_number, phone_number, email, created_at, migrated_at"
    restore_csv products "$tmpdir/products.csv" \
      "product_id, name, sku, price, migrated_at"
    restore_csv orders "$tmpdir/orders.csv" \
      "order_id, customer_id, order_date, status, total_amount, migrated_at"
    restore_csv line_items "$tmpdir/line_items.csv" \
      "line_item_id, order_id, product_id, quantity, unit_price"

    target_psql <<'SQL'
SELECT setval(pg_get_serial_sequence('customers','customer_id'), COALESCE((SELECT MAX(customer_id) FROM customers), 1));
SELECT setval(pg_get_serial_sequence('products','product_id'), COALESCE((SELECT MAX(product_id) FROM products), 1));
SELECT setval(pg_get_serial_sequence('orders','order_id'), COALESCE((SELECT MAX(order_id) FROM orders), 1));
SELECT setval(pg_get_serial_sequence('line_items','line_item_id'), COALESCE((SELECT MAX(line_item_id) FROM line_items), 1));
SQL
    ;;
  *)
    echo "Unsupported entity_name: $ENTITY (expected customers|products|orders)" >&2
    exit 1
    ;;
esac

echo "Subset seed complete."
