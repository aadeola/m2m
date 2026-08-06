#!/usr/bin/env bash
# Canonical way to start the shim. Always frees :8080 first and always binds
# to :8080 — regardless of any SERVER_PORT already set in the calling shell.
#
# :8080 is not a convention, it's a hard dependency: DLQ_API_BASE
# (dlq-agent.ts / npm run dlq), the DLQ cron job, and contract tests all
# assume the shim is reachable at exactly this port. Do not run ad hoc copies
# on other ports for debugging — use this script so only one instance, on
# the one port everything else expects, is ever running.
#
# Usage:
#   ./scripts/run-shim.sh                                        # normal startup
#   ./scripts/run-shim.sh -Dspring-boot.run.arguments=--backfill  # backfill job

set -euo pipefail

PORT=8080
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

existing_pids="$(lsof -ti:"${PORT}" 2>/dev/null || true)"
if [[ -n "${existing_pids}" ]]; then
  echo "Port ${PORT} is in use by pid(s): ${existing_pids} — killing before start..."
  kill -9 ${existing_pids} 2>/dev/null || true
  for _ in 1 2 3 4 5; do
    [[ -z "$(lsof -ti:"${PORT}" 2>/dev/null || true)" ]] && break
    sleep 1
  done
  if [[ -n "$(lsof -ti:"${PORT}" 2>/dev/null || true)" ]]; then
    echo "Port ${PORT} is still occupied after kill — aborting." >&2
    exit 1
  fi
fi

echo "Starting shim on :${PORT}..."
exec env SERVER_PORT="${PORT}" mvn spring-boot:run "$@"
