# Stretch Goal: Snowflake Analytics via OpenFlow

**Status:** Stretch goal — build only after the core migration shim is complete
and working. Do not attempt this during prep if time is short.

---

## 1. What This Adds

Once orders are living in MongoDB, you have a natural analytics story: pipe that
data into Snowflake via OpenFlow's MongoDB connector and run order trend analytics
on top of it. This shows the migration isn't just a database swap — it's also
unlocking a modern data platform that wasn't possible (or was much harder) with
the legacy Postgres setup.

**What the analytics show:**
- Order volume over time (using `order_date` from embedded documents)
- Order status breakdown (processing → shipped → delivered)
- Revenue trends (`total_amount` aggregated by day/week)
- Migration progress: migrated vs. pending record counts (useful to show during
  the migration window itself)

---

## 2. Architecture Addition

```
MongoDB (local Docker, replica set mode)
        │
        ▼
Snowflake OpenFlow
MongoDB Connector
(public preview as of Snowflake Summit 2026)
        │ initial full load + incremental sync
        │ via MongoDB change streams
        ▼
Snowflake Table: ORDERS (flattened from embedded documents)
        │
        ▼
Snowflake Analytics Queries / Dashboard
```

The OpenFlow connector sits entirely outside your Spring Boot application — it's
a Snowflake-managed pipeline that reads directly from MongoDB. No application code
changes needed.

---

## 3. Critical Constraint — Replica Set Required

The OpenFlow MongoDB connector uses MongoDB change streams to track incremental
updates. Change streams require MongoDB to run as a replica set or sharded cluster
— standalone instances are not supported.

**This is already required by your main project** (Spring's `MongoTransactionManager`
also needs replica set mode), so this isn't extra work — just confirm your
`docker-compose.yml` already starts Mongo in replica set mode:

```yaml
# docker-compose.yml — Mongo service with replica set enabled
mongo:
  image: mongo:7
  command: ["--replSet", "rs0", "--bind_ip_all"]
  ports:
    - "27017:27017"
  healthcheck:
    test: ["CMD", "mongosh", "--eval", "rs.status()"]
    interval: 10s
    retries: 5
```

After first start, initialize the replica set once:
```bash
docker exec -it <mongo-container> mongosh --eval "rs.initiate()"
```

---

## 4. OpenFlow Setup (Snowflake side)

OpenFlow is a managed service inside Snowflake — you configure it via Snowflake's
UI or SQL, not code you deploy. Steps:

1. **Create an OpenFlow deployment** in your Snowflake account (Snowflake
   Deployments option — managed via Snowpark Container Services, available on
   AWS/Azure/GCP; no BYOC needed for a demo)
2. **Install the MongoDB connector** from the connector library
3. **Configure the connector:**
   - Connection string: `mongodb://host.docker.internal:27017` (or your local IP
     if Snowflake can't reach `host.docker.internal` — see note below)
   - Collections to replicate: `orders`
   - Runtime size: Medium (minimum required for the MongoDB connector)
   - Min nodes / Max nodes: both set to `1` (multi-node not supported)
4. **Run the connector:** initial full load first, then incremental sync via
   change streams kicks in automatically

**Local connectivity note:** Snowflake's managed OpenFlow runtime runs in
Snowflake's cloud, so it can't reach `localhost` or `host.docker.internal` on
your laptop directly. For the demo you have two options:
- Use a **Snowflake OpenFlow BYOC deployment** running in your own AWS account
  (more setup, real network path)
- **Tunnel your local Mongo** via `ngrok` or `cloudflared` and give OpenFlow
  the public URL — fastest for a demo, not production practice, worth saying
  that explicitly
- **Alternatively:** skip the live OpenFlow connection and demo the Snowflake
  side with pre-loaded data, explaining that OpenFlow would normally feed it.
  The architecture story still lands.

---

## 5. Document Flattening

MongoDB stores orders as embedded documents. Snowflake tables are columnar.
OpenFlow flattens the document automatically, but nested arrays (`line_items`)
become a VARIANT column in Snowflake — you'll want a view that flattens it
further for clean analytics:

```sql
-- Snowflake view: flatten embedded line_items array
CREATE OR REPLACE VIEW order_line_items AS
SELECT
    o._id::STRING                        AS order_id,
    o.order_date::DATE                   AS order_date,
    o.status::STRING                     AS status,
    o.total_amount::FLOAT                AS total_amount,
    o.customer:id::STRING                AS customer_id,
    o.customer:name::STRING              AS customer_name,
    li.value:product_id::STRING          AS product_id,
    li.value:name::STRING                AS product_name,
    li.value:quantity::INT               AS quantity,
    li.value:unit_price::FLOAT           AS unit_price
FROM orders o,
LATERAL FLATTEN(input => o.line_items) li;
```

---

## 6. Analytics Queries

```sql
-- Order volume by day
SELECT order_date, COUNT(*) AS order_count
FROM orders
GROUP BY order_date
ORDER BY order_date;

-- Revenue trend by week
SELECT DATE_TRUNC('week', order_date) AS week,
       SUM(total_amount)              AS weekly_revenue
FROM orders
GROUP BY 1
ORDER BY 1;

-- Order status breakdown
SELECT status, COUNT(*) AS count
FROM orders
GROUP BY status;

-- Migration progress (during migration window)
-- migrated_at is replicated from Postgres alongside the Mongo data
SELECT
    COUNT_IF(migrated_at IS NOT NULL) AS migrated,
    COUNT_IF(migrated_at IS NULL)     AS pending,
    ROUND(COUNT_IF(migrated_at IS NOT NULL) / COUNT(*) * 100, 1) AS pct_complete
FROM orders;
```

---

## 7. What to Say About This in the Interview

**If you have time to build it:**
- *"Now that data is in Mongo, OpenFlow gives us a managed pipeline into
  Snowflake with no ETL code to maintain. The MongoDB connector does an initial
  full load then streams incremental changes via MongoDB change streams — the
  same oplog mechanism our replica set was already using."*
- *"Line items arrive as a VARIANT column since they're embedded arrays — a
  simple Snowflake view flattens them into a queryable format."*

**If you don't have time to build it (still mention it):**
- *"The natural next step after migration is analytics. Snowflake's OpenFlow
  MongoDB connector — currently in public preview — would give us a managed
  pipeline from Mongo into Snowflake with no ETL code. The replica set
  requirement was already a constraint in our setup, so we're already
  compatible."*

**Why it's a good "what would you do next" answer:**
It shows you're thinking past the migration itself to what the new data
architecture enables — which is exactly the enterprise framing the challenge
asks for.

---

## 8. Build Order (only if time permits, after all core phases are done)

- [ ] Confirm Mongo is running in replica set mode (`rs.initiate()`)
- [ ] Set up ngrok/cloudflared tunnel to expose local Mongo (if using Snowflake
  managed deployment)
- [ ] Create OpenFlow deployment in Snowflake
- [ ] Install and configure MongoDB connector, point at `orders` collection
- [ ] Run initial load, verify `orders` table in Snowflake
- [ ] Create `order_line_items` view to flatten `line_items` VARIANT
- [ ] Run analytics queries, show results

---

## 9. Limitations to Be Honest About

- Local Mongo → Snowflake connectivity requires a tunnel for a demo — not how
  you'd do it in production (VPC peering, private link, etc.)
- OpenFlow MongoDB connector is in **public preview** as of mid-2026 — worth
  flagging, not worth hiding
- Incremental sync delay is "a few minutes" — not real-time, which is fine for
  analytics but worth stating
- VARIANT flattening in Snowflake is manual — a dbt model would be the
  production-grade approach for maintaining that view
