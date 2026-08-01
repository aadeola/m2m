---
name: coverage-check
description: Verify every call site in inventory.json has a corresponding contract
  test. Use before any cutover decision, or when the discovery agent finds new
  call sites.
---
# Coverage Check

1. Read inventory.json — the list of every client call site the discovery agent found.
2. Scan src/test/java/.../contract for existing contract test files.
3. For each call site in inventory.json, check whether a corresponding contract
   test exists (match by endpoint path and HTTP method).
4. For any uncovered call site, use the MongoDB MCP server to pull a real sample
   document from the relevant collection — use actual field names and types to
   generate a stub contract test, not placeholders.
5. Report: which call sites are covered, which are not, and output stub tests
   for any gaps found.
