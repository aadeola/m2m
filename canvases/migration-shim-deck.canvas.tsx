import {
  Button,
  Callout,
  Card,
  CardBody,
  CardHeader,
  Code,
  Divider,
  Grid,
  H1,
  H2,
  H3,
  Pill,
  Row,
  Spacer,
  Stack,
  Stat,
  Table,
  Text,
  computeDAGLayout,
  mergeStyle,
  useCanvasState,
  useHostTheme,
} from "cursor/canvas";

const SLIDES = [
  "Title",
  "Problem",
  "Solution",
  "Architecture",
  "Design",
  "Classes",
  "Commands",
  "Agents & DLQ",
] as const;

export default function MigrationShimDeck() {
  const [index, setIndex] = useCanvasState("slide-index", 0);
  const slide = Math.max(0, Math.min(index, SLIDES.length - 1));

  const go = (next: number) =>
    setIndex(Math.max(0, Math.min(next, SLIDES.length - 1)));

  return (
    <Stack gap={16} style={{ padding: 8, minHeight: 520 }}>
      <Row gap={8} align="center" wrap>
        <Text weight="semibold" size="small">
          Migration Shim
        </Text>
        <Text tone="tertiary" size="small">
          {slide + 1} / {SLIDES.length}
        </Text>
        <Spacer />
        {SLIDES.map((label, i) => (
          <Pill key={label} active={i === slide} size="sm" onClick={() => go(i)}>
            {label}
          </Pill>
        ))}
      </Row>

      <Divider />

      <Stack gap={20} style={{ minHeight: 420 }}>
        {slide === 0 && <TitleSlide />}
        {slide === 1 && <ProblemSlide />}
        {slide === 2 && <SolutionSlide />}
        {slide === 3 && <ArchitectureSlide />}
        {slide === 4 && <DesignSlide />}
        {slide === 5 && <ClassesSlide />}
        {slide === 6 && <CommandsSlide />}
        {slide === 7 && <AgentsSlide />}
      </Stack>

      <Divider />

      <Row gap={8} align="center">
        <Button
          variant="secondary"
          disabled={slide === 0}
          onClick={() => go(slide - 1)}
        >
          Previous
        </Button>
        <Button
          variant="primary"
          disabled={slide === SLIDES.length - 1}
          onClick={() => go(slide + 1)}
        >
          Next
        </Button>
        <Spacer />
        <Text tone="tertiary" size="small">
          Java 21 · Spring Boot 3 · Postgres 16 · MongoDB 7
        </Text>
      </Row>

      <Text tone="quaternary" size="small">
        Source: AGENTS.md, mongo-migration-shim-plan.md, inventory.json
      </Text>
    </Stack>
  );
}

function TitleSlide() {
  const theme = useHostTheme();
  return (
    <Stack gap={20}>
      <Stack gap={8}>
        <Text tone="secondary" size="small" weight="medium">
          Cursor Field Engineering · Problem Space A
        </Text>
        <H1>RDBMS → MongoDB Migration Shim</H1>
        <Text tone="secondary">
          Keep legacy clients working while migrating customers, products, and
          orders from Postgres to denormalized MongoDB documents — with
          dual-write routing, shared transformers, and agent-driven contract
          coverage.
        </Text>
      </Stack>

      <Grid columns={4} gap={12}>
        <Stat value="3" label="Entities migrated" />
        <Stat value="Dual" label="DB routing window" tone="info" />
        <Stat value=":8080" label="Shim REST port" />
        <Stat value="Embed" label="Doc design bias" tone="success" />
      </Grid>

      <Row gap={8} wrap>
        <Pill active>Shim</Pill>
        <Pill active>Backfill</Pill>
        <Pill active>Contracts</Pill>
        <Pill active>DLQ</Pill>
      </Row>

      <div
        style={mergeStyle({
          padding: 12,
          borderRadius: 8,
          background: theme.fill.tertiary,
          border: `1px solid ${theme.stroke.tertiary}`,
        })}
      >
        <Text size="small" tone="secondary">
          Legacy clients keep the same REST shape. The shim decides Postgres vs
          Mongo per record via ID format + <Code>migrated_at</Code>, while a
          background backfill promotes existing rows in dependency order.
        </Text>
      </div>
    </Stack>
  );
}

function ProblemSlide() {
  return (
    <Stack gap={16}>
      <H1>The problem</H1>
      <Text tone="secondary">
        A business-critical service runs on Postgres. The org is moving to
        MongoDB for flexibility and scale — but the data model (and therefore
        the API contract) changes with it.
      </Text>

      <Grid columns={2} gap={12}>
        <Card>
          <CardHeader>Relational today</CardHeader>
          <CardBody>
            <Stack gap={8}>
              <Text size="small">
                <Code>customers</Code>, <Code>products</Code>,{" "}
                <Code>orders</Code>, <Code>line_items</Code>
              </Text>
              <Text size="small" tone="secondary">
                Joins, foreign keys, normalized rows. Clients already depend on
                the legacy REST field names and shapes.
              </Text>
            </Stack>
          </CardBody>
        </Card>
        <Card>
          <CardHeader>Document target</CardHeader>
          <CardBody>
            <Stack gap={8}>
              <Text size="small">
                Denormalized collections; line items embedded in orders
              </Text>
              <Text size="small" tone="secondary">
                Pointing clients at a new connection string is not enough — the
                read/write contract has to stay stable during the cutover window.
              </Text>
            </Stack>
          </CardBody>
        </Card>
      </Grid>

      <Callout tone="warning" title="Migration window risk">
        Live traffic continues while historical data backfills. Without a
        virtualization layer, clients would see broken contracts, split-brain
        reads, or a big-bang cutover.
      </Callout>
    </Stack>
  );
}

function SolutionSlide() {
  return (
    <Stack gap={16}>
      <H1>The solution</H1>
      <Text tone="secondary">
        Four coordinated pieces ship together so clients never notice which
        database is authoritative for a given record.
      </Text>

      <Grid columns={2} gap={12}>
        <Card>
          <CardHeader trailing={<Pill size="sm" active>Runtime</Pill>}>
            1. Shim layer
          </CardHeader>
          <CardBody>
            <Text size="small">
              Java 21 + Spring Boot 3 on <Code>:8080</Code>. Preserves legacy
              REST responses for customers, products, and orders — reads and
              writes — routing each record to Postgres or Mongo.
            </Text>
          </CardBody>
        </Card>
        <Card>
          <CardHeader trailing={<Pill size="sm" active>Batch</Pill>}>
            2. Backfill job
          </CardHeader>
          <CardBody>
            <Text size="small">
              Explicit <Code>--backfill</Code> runner migrates existing rows in
              order: customers → products → orders. Shares the same transformers
              as the live write path.
            </Text>
          </CardBody>
        </Card>
        <Card>
          <CardHeader trailing={<Pill size="sm" active>Proof</Pill>}>
            3. Contract tests
          </CardHeader>
          <CardBody>
            <Text size="small">
              One test per inventory endpoint, covering both migrated and
              unmigrated records so response shape stays identical either way.
            </Text>
          </CardBody>
        </Card>
        <Card>
          <CardHeader trailing={<Pill size="sm" active>Agents</Pill>}>
            4. Coverage + DLQ agents
          </CardHeader>
          <CardBody>
            <Text size="small">
              Coverage checks every <Code>inventory.json</Code> call site. DLQ
              triage reproduces poison batches in an isolated stack and opens
              fix PRs.
            </Text>
          </CardBody>
        </Card>
      </Grid>
    </Stack>
  );
}

function ArchitectureSlide() {
  const theme = useHostTheme();
  const labels: Record<string, string> = {
    clients: "Legacy clients",
    shim: "Shim :8080",
    resolver: "DataSourceResolver",
    pg: "Postgres",
    mongo: "MongoDB",
    backfill: "BackfillJob",
    xform: "Transformers",
  };

  const layout = computeDAGLayout({
    direction: "horizontal",
    nodeWidth: 120,
    nodeHeight: 36,
    rankGap: 56,
    nodeGap: 28,
    padding: 8,
    nodes: Object.keys(labels).map((id) => ({ id })),
    edges: [
      { from: "clients", to: "shim" },
      { from: "shim", to: "resolver" },
      { from: "resolver", to: "pg" },
      { from: "resolver", to: "mongo" },
      { from: "backfill", to: "xform" },
      { from: "xform", to: "mongo" },
      { from: "shim", to: "xform" },
      { from: "backfill", to: "pg" },
    ],
  });

  return (
    <Stack gap={16}>
      <H1>Architecture & routing</H1>
      <Text tone="secondary">
        Clients hit the shim. Routing uses ID shape first, then{" "}
        <Code>migrated_at</Code> for legacy numeric PKs.
      </Text>

      <div
        style={{
          position: "relative",
          width: layout.width,
          height: layout.height,
          maxWidth: "100%",
          overflow: "auto",
        }}
      >
        <svg
          width={layout.width}
          height={layout.height}
          style={{ display: "block" }}
        >
          {layout.edges.map((e) => (
            <line
              key={`${e.from}-${e.to}-${e.sourceX}`}
              x1={e.sourceX}
              y1={e.sourceY}
              x2={e.targetX}
              y2={e.targetY}
              stroke={theme.stroke.secondary}
              strokeWidth={1.5}
              strokeDasharray={e.isBackEdge ? "4 3" : undefined}
            />
          ))}
          {layout.nodes.map((n) => (
            <g key={n.id}>
              <rect
                x={n.x}
                y={n.y}
                width={120}
                height={36}
                rx={6}
                fill={theme.fill.tertiary}
                stroke={
                  n.id === "shim" || n.id === "resolver"
                    ? theme.accent.primary
                    : theme.stroke.tertiary
                }
              />
              <text
                x={n.x + 60}
                y={n.y + 22}
                textAnchor="middle"
                fill={theme.text.primary}
                fontSize={11}
                fontFamily="ui-sans-serif, system-ui, sans-serif"
              >
                {labels[n.id]}
              </text>
            </g>
          ))}
        </svg>
      </div>

      <Table
        headers={["ID shape", "Meaning", "Route"]}
        rows={[
          [
            "24-char hex ObjectId",
            "Born in Mongo after migration started",
            "Always Mongo",
          ],
          [
            "Numeric Postgres PK",
            "Legacy-origin record",
            "Mongo if migrated_at set, else Postgres",
          ],
        ]}
        striped
      />

      <Callout tone="info" title="Checkpoint ≠ migrated_at">
        Backfill resumability (last processed PK) is separate from whether a
        record is safe to read from Mongo. Do not conflate the two.
      </Callout>
    </Stack>
  );
}

function DesignSlide() {
  return (
    <Stack gap={16}>
      <H1>Design considerations</H1>
      <Text tone="secondary">
        Conventions that keep dual-write safe and the document model honest.
      </Text>

      <Grid columns={2} gap={12}>
        <Stack gap={10}>
          <H3>Contract fidelity</H3>
          <Text size="small">
            Shim endpoints must match the legacy response shape exactly — use
            Jackson <Code>@JsonProperty</Code> where Java names would diverge.
          </Text>

          <H3>One transform path</H3>
          <Text size="small">
            Never write Mongo documents outside the shim service layer or the
            backfill job. Shared transformers are the single source of truth for
            embedded document shape.
          </Text>

          <H3>Embed over lookup</H3>
          <Text size="small">
            Prefer embedding (customer summary + line items on orders).{" "}
            <Code>$lookup</Code> is an anti-pattern here unless embedding
            genuinely cannot fit (e.g. unbounded array growth).
          </Text>
        </Stack>

        <Stack gap={10}>
          <H3>Backfill resilience</H3>
          <Text size="small">
            Failed batches land in <Code>backfill_dlq</Code>; the job advances
            past the poison range and continues. Mongo schema validation (code
            121) becomes <Code>MongoSchemaValidationException</Code>.
          </Text>

          <H3>Dependency order</H3>
          <Text size="small">
            customers → products → orders, because orders embed denormalized
            copies of both. Line items are never a standalone Mongo collection.
          </Text>

          <H3>Inventory as truth</H3>
          <Text size="small">
            Discovery output <Code>inventory.json</Code> defines what needs a
            contract test. Admin DLQ APIs stay out of that inventory.
          </Text>
        </Stack>
      </Grid>
    </Stack>
  );
}

function ClassesSlide() {
  return (
    <Stack gap={16}>
      <H1>Important classes</H1>
      <Text tone="secondary">
        Package layout: controller / service / repository (jpa + mongo) /
        transform / dto / routing / job.
      </Text>

      <Table
        headers={["Class", "Role"]}
        rows={[
          [
            <Code>DataSourceResolver</Code>,
            "ObjectId → Mongo; numeric PK → migrated_at check → Postgres or Mongo",
          ],
          [
            <Code>OrderTransformer</Code>,
            "Shared PG→document and document→legacy response mapping (also Customer/Product)",
          ],
          [
            <Code>OrderService</Code>,
            "Read/write orchestration; routes via resolver; no third Mongo write path",
          ],
          [
            <Code>BackfillJob</Code>,
            "ApplicationRunner; runs only when --backfill is passed",
          ],
          [
            <Code>BackfillService</Code>,
            "Batched migrate + checkpoint + DLQ on failure; customers→products→orders",
          ],
          [
            <Code>*Controller</Code>,
            "Customer / Product / Order preserve legacy routes; BackfillDlqController is admin-only",
          ],
        ]}
        striped
      />

      <Grid columns={3} gap={12}>
        <Card>
          <CardHeader>JPA models</CardHeader>
          <CardBody>
            <Text size="small">
              CustomerEntity, ProductEntity, OrderEntity, LineItemEntity,
              BackfillCheckpointEntity, BackfillDlqEntity
            </Text>
          </CardBody>
        </Card>
        <Card>
          <CardHeader>Mongo models</CardHeader>
          <CardBody>
            <Text size="small">
              CustomerDocument, ProductDocument, OrderDocument +
              EmbeddedLineItem / EmbeddedProduct / EmbeddedCustomerSummary
            </Text>
          </CardBody>
        </Card>
        <Card>
          <CardHeader>Exceptions</CardHeader>
          <CardBody>
            <Text size="small">
              MongoSchemaValidationException, RecordNotFoundException — DLQ
              triage targets schema + bulk failures
            </Text>
          </CardBody>
        </Card>
      </Grid>
    </Stack>
  );
}

function CommandsSlide() {
  return (
    <Stack gap={16}>
      <H1>Key commands</H1>
      <Text tone="secondary">
        Local zero-hosting stack: Docker for databases, Maven for the shim,
        npm scripts for agent runners.
      </Text>

      <Table
        headers={["Command", "What it does"]}
        rows={[
          [
            <Code>docker-compose up -d</Code>,
            "Start Postgres + Mongo",
          ],
          [
            <Code>./scripts/verify-phase0.sh</Code>,
            "Validate Phase 0 infrastructure",
          ],
          [
            <Code>./scripts/seed-bulk.sh</Code>,
            "Load 1k customers / 100 products / 10k orders (~520k line items); resets Mongo validators",
          ],
          [
            <Code>./scripts/reset-postgres.sh</Code>,
            "Tiny init seed — use before contract tests after a bulk seed",
          ],
          [
            <Code>mvn spring-boot:run</Code>,
            "Start shim on :8080 (no backfill)",
          ],
          [
            <Code>mvn spring-boot:run -Dspring-boot.run.arguments=--backfill</Code>,
            "Run backfill job only",
          ],
          [
            <Code>mvn test</Code>,
            "Full suite; or -Dtest=*ContractTest for contracts only",
          ],
          [
            <Code>npm run coverage-check</Code>,
            "Inventory call-site coverage via Cursor SDK",
          ],
          [
            <Code>npm run dlq</Code>,
            "Poll /admin/backfill/dlq and triage via Cursor SDK",
          ],
        ]}
        striped
        stickyHeader
      />
    </Stack>
  );
}

function AgentsSlide() {
  return (
    <Stack gap={16}>
      <H1>Agents & DLQ triage</H1>
      <Text tone="secondary">
        Cursor agents drive discovery, generation, coverage, and poison-batch
        repair without writing to prod or marking DLQ rows resolved.
      </Text>

      <Grid columns={2} gap={12}>
        <Stack gap={10}>
          <H2>Agent pipeline</H2>
          <Table
            headers={["Stage", "Output"]}
            framed={false}
            rows={[
              ["Discovery", "inventory.json call sites"],
              ["Shim generation", "Controllers + services"],
              ["Contract tests", "Migrated + unmigrated cases"],
              ["Coverage check", "No inventory gaps before cutover"],
              ["DLQ triage", "Isolated reproduce → fix PR"],
            ]}
          />
        </Stack>

        <Stack gap={10}>
          <H2>DLQ safety rails</H2>
          <Text size="small">
            Single-agent flock on <Code>.dlq-agent.lock</Code>. Processes only{" "}
            <Code>MongoSchemaValidationException</Code> and{" "}
            <Code>BulkOperationException</Code>.
          </Text>
          <Text size="small">
            Branch from <Code>origin/main</Code>:{" "}
            <Code>dlq-fix/&lt;entity&gt;-&lt;startPk&gt;-&lt;endPk&gt;</Code>.
            Skip if that PR is already open. Tear down{" "}
            <Code>docker compose -p dlq-&lt;id&gt; … down -v</Code>.
          </Text>
          <Callout tone="success" title="Isolated stack">
            <Text size="small">
              <Code>docker-compose.dlq.yml</Code> — Postgres{" "}
              <Code>15432</Code>, Mongo <Code>37017</Code>. Seed with{" "}
              <Code>./scripts/dlq-seed-subset.sh</Code>.
            </Text>
          </Callout>
        </Stack>
      </Grid>

      <Divider />

      <H3>Takeaway</H3>
      <Text>
        Dual-database routing + shared transformers + inventory-backed contracts
        let you migrate without rewriting clients — and poison batches become
        automated fix PRs instead of stopped pipelines.
      </Text>
    </Stack>
  );
}
