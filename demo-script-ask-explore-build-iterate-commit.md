# Demo Script: Ask → Explore → Build → Iterate/Test → Commit

**Goal:** Every stage of the migration-shim build maps to a stage of the cycle, and
each stage is a chance to show a specific Cursor feature — not just "watch AI write code."
Runtime target: ~20 min demo + live extension. (Stage 3b, the backfill demo, adds
real substance but keep it tight — this is the story, not extra runtime to protect.)

---

## Stage 0 — Cold Open (30 sec)
**Say:** "I'm modernizing a legacy Postgres service migrating to MongoDB. The database
change forces an API contract change, which breaks every downstream client. I'm going
to show the whole Ask→Explore→Build→Iterate→Commit loop, and along the way I'll hit
most of what Cursor gives you: Ask mode, Plan mode, background agents, Rules, Skills,
MCP as a live database operations tool throughout, and model switching."

---

## Stage 1 — ASK (2–3 min)
**What you're doing:** Using Cursor in **Ask mode** (read-only, no edits) to interrogate
the existing codebase before touching anything.

**Script:**
- Open Ask mode. Prompt: *"Explain how the legacy API's `/orders` endpoint queries
  Postgres — what joins does it rely on, and what would break if this were Mongo?"*
- Cursor reads the code, explains the joins (orders → customers → line_items),
  and flags the transaction assumptions.

**Feature highlight:** Ask mode is read-only by design — no risk of the agent touching
code while you're still exploring. Good moment to say: *"This is where I'd bring in a
teammate to sanity check before we write anything."*

---

## Stage 2 — EXPLORE (3–4 min)
**What you're doing:** Still exploratory, but now using the **MongoDB MCP server**
as a live database operations tool inside Cursor, and kicking off the discovery agent.

**Script:**
- Show `.cursor/mcp.json` with the MongoDB MCP server configured against your
  local Docker Mongo instance.
- Prompt: *"Using the MongoDB MCP tools, query the `orders` collection and show
  me what documents are currently there."* — Cursor queries live Mongo and returns
  real documents. No terminal, no mongosh. **Say:** *"Every Mongo operation in
  this demo goes through MCP — I'm not switching to a terminal at any point."*
- Use MCP to insert a couple of seed documents directly from Cursor, to have
  data ready for the next stages.
- Kick off the **discovery agent** in the background: *"Scan `/clients/*` for
  every call site against the legacy REST API and write `inventory.json`."* Let
  it run while you narrate.

**Feature highlight:** MCP as a general database operations tool — reads, writes,
queries, all from inside Cursor. Background agents letting you keep working while
a longer task runs in parallel.

---

## Stage 3 — BUILD (5–6 min)
**What you're doing:** Switching to **Agent mode**, using **Plan Mode** first for the
shim itself since it's non-trivial, then letting the agent implement.

**Script:**
- Prompt in Plan Mode: *"Propose a plan for a Spring Boot shim service that exposes
  the legacy `/orders` API shape but reads/writes MongoDB instead of Postgres.
  Design the document model favoring embedding over joins or `$lookup` — `$lookup`
  is a Mongo anti-pattern here since line items are always read with their order."*
- Review the plan out loud — this is a good moment to push back on one detail live
  (e.g., "denormalize product name and price into each embedded line item so we
  don't need a runtime lookup against the products collection at all") to show
  you're directing, not just accepting.
- Approve → agent implements: `@RestController`, `@Document` model with an
  embedded `List<LineItem>`, `MongoRepository`. Show `AGENTS.md` in the root and
  mention: *"This is always-on context — the agent already knows the package
  structure and conventions before I typed anything, because of this file."*
- **Model switch moment:** mention/show switching between a fast model for the
  straightforward CRUD scaffolding (controllers, DTOs) and a frontier reasoning
  model (e.g., Opus-class) for the trickier document-design logic — deciding what
  to embed vs. what stays a lightweight reference. *"I don't need the most
  expensive model to scaffold a Spring controller, but I want the strongest one
  reasoning about which fields need to be denormalized so we never have to fall
  back to a join."*

**Feature highlight:** Plan Mode (reviewable plan before edits), AGENTS.md (persistent
project context), model picker (right-sizing cost/quality per task).

---

## Stage 3b — BACKFILL (the migration itself, 3–4 min)
**What you're doing:** Showing the actual data migration running live, and using
MCP to verify it without ever leaving Cursor.

**Script:**
- Show the `orders` table with rows where `migrated_at IS NULL`.
- Run the backfill job. Narrate: *"This shares the same `OrderTransformer` the
  shim's write path uses — one definition of what a migrated order looks like,
  whether it's a live request or a batch job."*
- While it runs, prompt MCP: *"Query the orders collection and show me what's
  landed so far."* Watch documents appear in real time — from inside Cursor.
- Hit the same `/orders/{id}` endpoint before and after a row is backfilled —
  identical response both times. **Say:** *"The client can't tell which database
  served that. That's the point."*

**Feature highlight:** MCP used here as a live verification tool — no terminal
switch mid-demo. Clean, uninterrupted Cursor experience.

---

## Stage 4 — ITERATE / TEST (4–5 min)
**What you're doing:** Contract tests as the feedback loop; deliberately breaking
something to show the loop catching it.

**Script:**
- Prompt: *"Generate a JUnit 5 contract test for each entry in `inventory.json`
  that hits both the legacy endpoint and the shim, and fails on any field
  mismatch. Use REST Assured. Use the MongoDB MCP server to pull a real sample
  document to inform the test data — real field names, not placeholders. Include
  a case for an unmigrated record and a migrated one."*
- Run the suite green (`mvn test`). Then: *"Now edit the shim to drop the
  embedded `line_items` from the order write path"* — rerun tests, show the
  failure, then ask the agent to fix it based on the test output.
- **Feature highlight:** This is the "agent iterates until tests pass" loop — a
  verifiable goal (tests) makes the agent self-correct instead of you manually
  proofreading every diff.

---

## Stage 5 — COVERAGE CHECK (the "wow" moment, 3–4 min)
**What you're doing:** Invoking the **Skill** you built — dynamically loaded,
not always-on — which uses MCP to generate real stub tests for any gaps found.

**Script:**
- Live-edit: add a new call site to one of the client repos (a new endpoint call).
- Invoke the skill: `/coverage-check` (or let the agent decide based on
  description-based matching).
- Skill reads `inventory.json`, scans contract tests, finds the uncovered call
  site. Then uses **MCP to query Mongo for a real sample document** for that
  endpoint and generates a stub contract test with actual field names and types.
- **Say:** *"The agent didn't guess at the document shape — it queried the live
  database via MCP and used what's actually there. That's the difference between
  a stub test that passes and one that actually validates the contract."*

**Feature highlight:** Skills vs Rules (on-demand vs always-on); MCP used here
as a data-informed test generation tool, not just a query interface.

---

## Stage 5b — CURSOR SDK (~1 min)
**What you're doing:** Showing the coverage-check skill is callable outside the
editor — the piece that satisfies "build using the Cursor SDK."

**Script:**
- Switch to the terminal, run `npm run coverage-check`.
- This executes `coverage-check-runner.ts`, which imports `Agent` from
  `@cursor/sdk`, creates an agent, and prompts it to run the same coverage-check
  skill — including the MCP call to pull real documents for any gaps.
- **Say:** *"Everything so far was me driving the Cursor IDE. This one script is
  the same skill, but callable from CI as a gate before any cutover decision.
  That's the SDK."*

**Feature highlight:** Cursor SDK — programmatic agent creation outside the IDE.
Brief — one file, makes the point cleanly.

---

## Stage 6 — COMMIT (1–2 min)
**What you're doing:** Closing the loop like a real engineer would.

**Script:**
- Ask the agent to generate a descriptive commit message summarizing the shim,
  backfill job, contract tests, and coverage-check skill.
- `git add . && git commit -m "..."` — mention that in a real team setting this
  is where Rules could enforce commit message conventions, and a background agent
  could open the PR, run the coverage-check as a CI gate via the SDK, and block
  merge until every call site has a test.

---

## Live Extension (reserve ~5 min, interviewer-directed)
Be ready to react to prompts like:
- "Add a new client call site — show drift gets flagged." → repeat Stage 2 discovery +
  Stage 5 drift-check on the new site.
- "Make the shim handle a transaction pattern Mongo doesn't support." → back to Plan
  Mode, discuss trade-offs live (multi-document transactions vs. eventual consistency).
- "What if this were 200 services, not 3?" → talk scaling: background agents running
  discovery in parallel across repos, Rules enforced at the team level, Skills shared
  via `.claude/skills` cross-tool discoverability.
- "What happens if a record is updated mid-backfill, right between the read and
  the `migrated_at` write?" → discuss the narrow race window honestly, and how
  you'd harden it (e.g. a brief per-record lock, or accepting last-write-wins
  for a demo-scale system and noting CDC as the production-grade fix).

---

## Feature Checklist (make sure you hit all of these on camera)
- [ ] Ask mode (read-only exploration)
- [ ] MCP (MongoDB — database operations throughout: seed data, backfill
  verification, document inspection for test generation)
- [ ] Background/cloud agent (discovery running async)
- [ ] Plan Mode (reviewable plan before edits)
- [ ] Agent mode (implementation)
- [ ] Model switching (fast model vs. frontier model by task difficulty)
- [ ] Rules / AGENTS.md (always-on context)
- [ ] Skills (on-demand procedural workflow — coverage-check)
- [ ] Cursor SDK (`@cursor/sdk` — coverage-check-runner.ts callable outside the editor)
- [ ] Test-driven iteration loop (break it, watch it self-correct)
- [ ] Commit message generation
