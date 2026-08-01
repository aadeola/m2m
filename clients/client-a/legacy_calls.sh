#!/usr/bin/env bash
# Fake client service A — calls legacy order and product endpoints.
# Used by the Phase 1 discovery agent to build inventory.json.

set -euo pipefail

LEGACY_API_BASE="${LEGACY_API_BASE:-http://localhost:8080}"

curl -sS "${LEGACY_API_BASE}/products/1"
curl -sS "${LEGACY_API_BASE}/orders/1"
curl -sS "${LEGACY_API_BASE}/orders?customer_id=1"
curl -sS -X POST "${LEGACY_API_BASE}/orders" \
  -H "Content-Type: application/json" \
  -d '{"customer_id":1,"line_items":[{"product_id":1,"quantity":1}]}'
