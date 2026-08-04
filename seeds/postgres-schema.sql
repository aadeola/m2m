-- Schema-only init for isolated DLQ environments (no sample data).

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
