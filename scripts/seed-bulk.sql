-- Bulk seed for multi-minute backfill demo.
-- Destructive rewrite: truncates business tables and reloads a deterministic dataset.
-- Targets: 5k customers, 100 products, 50k orders, 5–100 line items per order (~2.6M rows).
-- Each order has exactly one customer_id; customers may have multiple orders.

BEGIN;

TRUNCATE TABLE line_items, orders, products, customers RESTART IDENTITY CASCADE;

UPDATE backfill_checkpoint
SET last_processed_pk = 0;

INSERT INTO customers (name, email)
SELECT
    'Customer ' || i,
    'customer' || i || '@example.com'
FROM generate_series(1, 5000) AS s(i);

INSERT INTO products (name, sku, price)
SELECT
    'Product ' || i,
    'SKU-' || lpad(i::text, 3, '0'),
    (9.99 + ((i - 1) % 50) * 2.00)::numeric(10, 2)
FROM generate_series(1, 100) AS s(i);

INSERT INTO orders (customer_id, order_date, status, total_amount)
SELECT
    1 + ((i - 1) % 5000),
    DATE '2024-01-01' + ((i - 1) % 730),
    (ARRAY['PENDING', 'SHIPPED', 'DELIVERED', 'CANCELLED'])[1 + ((i - 1) % 4)],
    0.00
FROM generate_series(1, 50000) AS s(i);

INSERT INTO line_items (order_id, product_id, quantity, unit_price)
SELECT
    o.order_id,
    p.product_id,
    1 + ((o.order_id + n) % 5),
    p.price
FROM orders o
CROSS JOIN LATERAL generate_series(1, 5 + (o.order_id % 96)) AS g(n)
JOIN products p ON p.product_id = 1 + ((o.order_id + n) % 100);

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
