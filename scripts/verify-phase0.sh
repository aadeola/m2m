#!/usr/bin/env bash
# Validates Phase 0 infrastructure: Docker services, Postgres seed data, Mongo replica set,
# Spring Boot scaffold, and client call-site fixtures.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

pass=0
fail=0

ok() {
  echo "  PASS: $1"
  pass=$((pass + 1))
}

err() {
  echo "  FAIL: $1"
  fail=$((fail + 1))
}

section() {
  echo
  echo "== $1 =="
}

section "Required files"
for f in \
  docker-compose.yml \
  seeds/postgres-seed.sql \
  pom.xml \
  AGENTS.md \
  .cursor/mcp.json \
  .cursor/skills/taxonomy-coverage/SKILL.md \
  clients/client-a/legacy_calls.sh \
  clients/client-b/legacy_calls.mjs \
  clients/client-c/legacy_calls.py; do
  if [[ -f "$f" ]]; then
    ok "$f exists"
  else
    err "$f missing"
  fi
done

section "Docker services"
if ! command -v docker >/dev/null 2>&1; then
  err "docker not installed"
else
  ok "docker CLI available"
  docker compose up -d

  for _ in $(seq 1 30); do
    if docker compose ps --status running 2>/dev/null | grep -q postgres && \
       docker compose ps --status running 2>/dev/null | grep -q mongo; then
      break
    fi
    sleep 2
  done

  if docker compose ps postgres 2>/dev/null | grep -qE "healthy|running"; then
    ok "Postgres container running"
  else
    err "Postgres container not healthy"
  fi

  if docker compose ps mongo 2>/dev/null | grep -qE "healthy|running"; then
    ok "Mongo container running"
  else
    err "Mongo container not healthy"
  fi
fi

section "Postgres seed data"
if command -v docker >/dev/null 2>&1; then
  customer_count="$(docker compose exec -T postgres psql -U postgres -d migration -tAc "SELECT COUNT(*) FROM customers;" 2>/dev/null | tr -d '[:space:]' || echo 0)"
  order_count="$(docker compose exec -T postgres psql -U postgres -d migration -tAc "SELECT COUNT(*) FROM orders;" 2>/dev/null | tr -d '[:space:]' || echo 0)"
  line_item_count="$(docker compose exec -T postgres psql -U postgres -d migration -tAc "SELECT COUNT(*) FROM line_items;" 2>/dev/null | tr -d '[:space:]' || echo 0)"
  product_count="$(docker compose exec -T postgres psql -U postgres -d migration -tAc "SELECT COUNT(*) FROM products;" 2>/dev/null | tr -d '[:space:]' || echo 0)"

  if [[ "${customer_count:-0}" -ge 5 ]]; then ok "customers >= 5 ($customer_count)"; else err "customers >= 5 ($customer_count)"; fi
  if [[ "${order_count:-0}" -ge 10 ]]; then ok "orders >= 10 ($order_count)"; else err "orders >= 10 ($order_count)"; fi
  if [[ "${line_item_count:-0}" -ge 20 ]]; then ok "line_items >= 20 ($line_item_count)"; else err "line_items >= 20 ($line_item_count)"; fi
  if [[ "${product_count:-0}" -ge 1 ]]; then ok "products table seeded ($product_count)"; else err "products table seeded ($product_count)"; fi
fi

section "Mongo replica set"
if command -v docker >/dev/null 2>&1; then
  rs_ok="$(docker compose exec -T mongo mongosh --quiet --eval "try { rs.status().ok } catch (e) { 0 }" 2>/dev/null | tr -d '[:space:]' || echo 0)"
  if [[ "$rs_ok" == "1" ]]; then
    ok "Mongo replica set initialized (rs0)"
  else
    err "Mongo replica set not initialized — run: docker compose up mongo-init"
  fi
fi

section "Spring Boot scaffold"
if command -v mvn >/dev/null 2>&1; then
  ok "Maven available"
  if mvn -q test; then
    ok "mvn test passes"
  else
    err "mvn test failed"
  fi
else
  err "Maven not installed — install Maven to run mvn test / mvn spring-boot:run"
fi

section "Client call-site fixtures"
call_sites="$(grep -RhE '(/orders|/customers)' clients/ 2>/dev/null | wc -l | tr -d '[:space:]' || echo 0)"
if [[ "${call_sites:-0}" -ge 7 ]]; then
  ok "client repos contain >= 7 legacy API references ($call_sites)"
else
  err "client repos contain >= 7 legacy API references ($call_sites)"
fi

section "Summary"
echo "Passed: $pass  Failed: $fail"
if [[ "$fail" -gt 0 ]]; then
  echo
  echo "Phase 0 verification FAILED."
  exit 1
fi

echo
echo "Phase 0 verification PASSED."
echo
echo "Next steps:"
echo "  1. mvn spring-boot:run     # starts empty shim on :8080 (requires Docker DBs up)"
echo "  2. Proceed to Phase 1       # discovery agent scans clients/ for inventory.json"
