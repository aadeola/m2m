---
name: coverage-check
description: Verify every call site in inventory.json has a corresponding contract
  test. Use before any cutover decision, or when the discovery agent finds new
  call sites.
---
# Coverage Check

Verify that every client call site in `inventory.json` is covered by a contract test
before any cutover decision.

## Coverage Semantics

Coverage is **call-site coverage via endpoint/method mapping**, not one test file
per inventory entry. Multiple call sites may map to the same endpoint contract and
therefore the same contract test class.

Match each inventory entry by **`method + endpoint`** (exact string from
`inventory.json`, including query strings like `/orders?customer_id={customer_id}`).

Canonical endpoint → contract test mapping (see also
`scripts/coverage-check-core.ts` and
`src/test/java/com/migration/contract/support/InventoryCatalog.java`):

| Method | Endpoint | Contract Test Class |
|--------|----------|---------------------|
| GET | `/products/{id}` | `GetProductByIdContractTest` |
| GET | `/orders/{id}` | `GetOrderByIdContractTest` |
| GET | `/orders?customer_id={customer_id}` | `GetOrdersByCustomerIdContractTest` |
| POST | `/orders` | `PostOrderContractTest` |
| GET | `/customers` | `GetCustomersContractTest` |
| GET | `/customers/{id}` | `GetCustomerByIdContractTest` |
| GET | `/orders` | `GetOrdersContractTest` |
| GET | `/orders/{id}/status` | `GetOrderStatusContractTest` |
| GET | `/customers/{id}/orders` | `GetCustomerOrdersContractTest` |

A call site is **covered** when its endpoint contract has a corresponding
`*ContractTest.java` file in `src/test/java/com/migration/contract/`.

## Steps

1. Read `inventory.json` — every client call site the discovery agent found.
2. For each entry, resolve its endpoint contract using the table above.
3. Scan `src/test/java/com/migration/contract/` for `*ContractTest.java` files.
4. Cross-check with `InventoryCatalog.java` when verifying many-to-one mappings.
5. For any **uncovered** call site, use the **MongoDB MCP server** to pull a real
   sample document from the relevant collection (`customers`, `products`, or
   `orders`) and generate a stub contract test with actual field names — not
   placeholders.
6. Emit a structured report (see Output Format below).

## Output Format

Always end with a JSON block wrapped in ` ```json ` fences containing:

```json
{
  "total_call_sites": 12,
  "covered_call_sites": [
    {
      "endpoint": "/products/{id}",
      "method": "GET",
      "owning_service": "Client Service A",
      "source_file": "clients/client-a/legacy_calls.sh",
      "test_class": "GetProductByIdContractTest"
    }
  ],
  "uncovered_call_sites": [],
  "covered_endpoints": [
    {
      "method": "GET",
      "endpoint": "/products/{id}",
      "testClass": "GetProductByIdContractTest"
    }
  ],
  "missing_endpoints": [],
  "stub_test_suggestions": []
}
```

Field definitions:
- `covered_call_sites` — inventory entries with a matching contract test
- `uncovered_call_sites` — inventory entries with no matching test (include `reason`)
- `covered_endpoints` — unique endpoint contracts that have tests
- `missing_endpoints` — endpoint contracts with no test file
- `stub_test_suggestions` — for gaps only; include `mongo_collection` hint

## Decision Rule

- **Pass:** `uncovered_call_sites` is empty — safe to proceed toward cutover.
- **Fail:** any uncovered call site — report gaps and do **not** cut over until
  contract tests exist for every call site.
