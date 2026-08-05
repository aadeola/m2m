-- Bulk seed for multi-minute backfill demo.
-- Destructive rewrite: truncates business tables and reloads a deterministic dataset.
-- Targets: 1k customers, 100 products, 10k orders, 5–100 line items per order (~520k rows).
-- Each order has exactly one customer_id; customers may have multiple orders.
-- Poison isolation: every order EXCEPT order 100 references only "safe" products
-- (1–30, 41–100). Order 100 is the sole order embedding products 31–40 (the batch
-- the product-37 trigger forces into the DLQ), so exactly one order batch fails
-- Mongo schema validation instead of cascading across the whole run.

BEGIN;

CREATE TABLE IF NOT EXISTS backfill_dlq (
    id              SERIAL PRIMARY KEY,
    entity_name     VARCHAR(32) NOT NULL,
    start_pk        INTEGER NOT NULL,
    end_pk          INTEGER NOT NULL,
    exception_class VARCHAR(255) NOT NULL,
    message         TEXT,
    occurred_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    resolved        BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_at     TIMESTAMP NULL
);

CREATE OR REPLACE FUNCTION prevent_product_37_migration()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.product_id = 37
       AND NEW.migrated_at IS NOT NULL
       AND OLD.migrated_at IS NULL THEN
        RAISE EXCEPTION 'Product 37 is locked pending vendor pricing review and cannot be migrated';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_prevent_product_37_migration ON products;
CREATE TRIGGER trg_prevent_product_37_migration
    BEFORE UPDATE ON products
    FOR EACH ROW
    EXECUTE FUNCTION prevent_product_37_migration();

TRUNCATE backfill_dlq RESTART IDENTITY;

UPDATE backfill_checkpoint
SET last_processed_pk = 0;

TRUNCATE TABLE line_items, orders, products, customers RESTART IDENTITY CASCADE;

INSERT INTO customers (first_name, last_name, account_number, phone_number, email)
SELECT
    'First' || chr(65 + ((i - 1) % 26)),
    'Last' || chr(65 + ((i - 1) % 26)),
    'CUS' || lpad((i % 10000)::text, 4, '0'),
    '555' || lpad(((i - 1) % 10000000)::text, 7, '0'),
    'customer' || i || '@example.com'
FROM generate_series(1, 1000) AS s(i);

UPDATE customers SET email = 'customer100.example.com' WHERE customer_id = 100;
UPDATE customers SET phone_number = '5551234'           WHERE customer_id = 101;
UPDATE customers SET account_number = 'AB12345'         WHERE customer_id = 102;
UPDATE customers SET first_name = 'A'                   WHERE customer_id = 103;
UPDATE customers SET last_name = 'Smith1'                 WHERE customer_id = 104;

INSERT INTO products (name, sku, price)
SELECT
    'Product ' || i,
    'SKU-' || lpad(i::text, 3, '0'),
    (9.99 + ((i - 1) % 50) * 2.00)::numeric(10, 2)
FROM generate_series(1, 100) AS s(i);

INSERT INTO orders (customer_id, order_date, status, total_amount)
SELECT
    1 + ((i - 1) % 1000),
    DATE '2024-01-01' + ((i - 1) % 730),
    (ARRAY['PENDING', 'SHIPPED', 'DELIVERED', 'CANCELLED'])[1 + ((i - 1) % 4)],
    0.00
FROM generate_series(1, 10000) AS s(i);

-- All orders except order 100 reference only safe products (1–30, 41–100). The
-- 90-wide modulo maps 0–29 -> 1–30 and 30–89 -> 41–100, skipping 31–40 entirely.
INSERT INTO line_items (order_id, product_id, quantity, unit_price)
SELECT
    o.order_id,
    p.product_id,
    1 + ((o.order_id + n) % 5),
    p.price
FROM orders o
CROSS JOIN LATERAL generate_series(1, 5 + (o.order_id % 96)) AS g(n)
JOIN products p ON p.product_id =
    CASE
        WHEN ((o.order_id + n) % 90) < 30 THEN ((o.order_id + n) % 90) + 1
        ELSE ((o.order_id + n) % 90) + 11
    END
WHERE o.order_id <> 100;

-- Order 100 is the single poison order: it embeds every product in 31–40, so its
-- document references products the trigger prevents from migrating.
INSERT INTO line_items (order_id, product_id, quantity, unit_price)
SELECT 100, p.product_id, 1, p.price
FROM products p
WHERE p.product_id BETWEEN 31 AND 40;

UPDATE orders o
SET total_amount = sub.total
FROM (
    SELECT order_id, SUM(quantity * unit_price)::numeric(10, 2) AS total
    FROM line_items
    GROUP BY order_id
) sub
WHERE o.order_id = sub.order_id;

COMMIT;

SELECT 'customers' AS entity, COUNT(*) AS count FROM customers
UNION ALL
SELECT 'products', COUNT(*) FROM products
UNION ALL
SELECT 'orders', COUNT(*) FROM orders
UNION ALL
SELECT 'line_items', COUNT(*) FROM line_items
ORDER BY entity;

-- Poison isolation check: only order 100 should reference products 31–40.
SELECT
    COUNT(DISTINCT order_id) AS distinct_orders_referencing_31_40,
    MIN(order_id) AS first_such_order,
    MAX(order_id) AS last_such_order
FROM line_items
WHERE product_id BETWEEN 31 AND 40;
