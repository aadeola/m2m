/**
 * Fake client service B — calls legacy customer, product, and order endpoints.
 * Used by the Phase 1 discovery agent to build inventory.json.
 */

const LEGACY_API_BASE = process.env.LEGACY_API_BASE ?? "http://localhost:8080";

async function fetchLegacy(path, options = {}) {
  const response = await fetch(`${LEGACY_API_BASE}${path}`, options);
  return response.json();
}

await fetchLegacy("/products/2");
await fetchLegacy("/customers");
await fetchLegacy("/customers/2");
await fetchLegacy("/orders/3", { method: "GET" });
