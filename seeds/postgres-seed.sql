-- Legacy Postgres schema and sample data for migration shim demo

CREATE TABLE customers (
    customer_id    SERIAL PRIMARY KEY,
    first_name     VARCHAR(255) NOT NULL,
    last_name      VARCHAR(255) NOT NULL,
    account_number VARCHAR(7) NOT NULL,
    phone_number   VARCHAR(32) NOT NULL,
    email          VARCHAR(255) NOT NULL UNIQUE,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    migrated_at    TIMESTAMP NULL
);

CREATE TABLE products (
    product_id SERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    sku        VARCHAR(64) NOT NULL UNIQUE,
    price      DECIMAL(10, 2) NOT NULL,
    migrated_at TIMESTAMP NULL
);

CREATE TABLE orders (
    order_id     SERIAL PRIMARY KEY,
    customer_id  INTEGER NOT NULL REFERENCES customers (customer_id),
    order_date   DATE NOT NULL,
    status       VARCHAR(32) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    migrated_at TIMESTAMP NULL
);

CREATE TABLE line_items (
    line_item_id SERIAL PRIMARY KEY,
    order_id     INTEGER NOT NULL REFERENCES orders (order_id),
    product_id   INTEGER NOT NULL REFERENCES products (product_id),
    quantity     INTEGER NOT NULL CHECK (quantity > 0),
    unit_price   DECIMAL(10, 2) NOT NULL
);

CREATE TABLE backfill_checkpoint (
    entity_name       VARCHAR(32) PRIMARY KEY,
    last_processed_pk INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE backfill_dlq (
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

INSERT INTO backfill_checkpoint (entity_name, last_processed_pk) VALUES
    ('customers', 0),
    ('products', 0),
    ('orders', 0);

INSERT INTO customers (first_name, last_name, account_number, phone_number, email) VALUES
    ('Alice', 'Johnson', 'CUS0001', '5550000001', 'alice@example.com'),
    ('Bob', 'Smith', 'CUS0002', '5550000002', 'bob@example.com'),
    ('Carol', 'Davis', 'CUS0003', '5550000003', 'carol@example.com'),
    ('Dan', 'Lee', 'CUS0004', '5550000004', 'dan@example.com'),
    ('Eve', 'Martinez', 'CUS0005', '5550000005', 'eve@example.com');

INSERT INTO products (name, sku, price) VALUES
    ('Wireless Mouse', 'WM-001', 29.99),
    ('Mechanical Keyboard', 'MK-002', 89.99),
    ('USB-C Hub', 'HUB-003', 49.99),
    ('Monitor Stand', 'MS-004', 39.99),
    ('Webcam HD', 'WC-005', 59.99),
    ('Laptop Sleeve', 'LS-006', 24.99);

INSERT INTO orders (customer_id, order_date, status, total_amount) VALUES
    (1, '2025-01-10', 'SHIPPED', 119.98),
    (1, '2025-02-14', 'DELIVERED', 89.99),
    (2, '2025-01-22', 'PENDING', 49.99),
    (2, '2025-03-01', 'SHIPPED', 149.97),
    (3, '2025-02-05', 'DELIVERED', 59.99),
    (3, '2025-03-15', 'CANCELLED', 24.99),
    (4, '2025-01-30', 'SHIPPED', 79.98),
    (4, '2025-04-02', 'PENDING', 89.99),
    (5, '2025-02-20', 'DELIVERED', 169.96),
    (5, '2025-04-10', 'SHIPPED', 39.99);

INSERT INTO line_items (order_id, product_id, quantity, unit_price) VALUES
    (1, 1, 2, 29.99),
    (1, 4, 1, 39.99),
    (1, 6, 1, 24.99),
    (2, 2, 1, 89.99),
    (3, 3, 1, 49.99),
    (4, 1, 1, 29.99),
    (4, 2, 1, 89.99),
    (4, 5, 1, 59.99),
    (5, 5, 1, 59.99),
    (6, 6, 1, 24.99),
    (7, 1, 1, 29.99),
    (7, 3, 1, 49.99),
    (8, 2, 1, 89.99),
    (9, 2, 1, 89.99),
    (9, 3, 1, 49.99),
    (9, 5, 1, 59.99),
    (9, 4, 1, 39.99),
    (10, 4, 1, 39.99),
    (10, 1, 1, 29.99),
    (10, 6, 1, 24.99);
