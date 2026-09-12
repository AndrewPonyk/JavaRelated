-- Migration 0001: bootstrap the demo schema.
--
-- MiniDB does not yet persist its system catalog (that is a Phase 3 item in
-- docs/PROJECT-PLAN.md), so "migrations" are plain SQL scripts replayed on a
-- fresh database to recreate the expected tables. They double as the canonical
-- example of the schema/relationships this engine is built around.
--
-- Apply with:  minidb app.db < migrations/0001_init_catalog.sql

-- Customers (id is the primary key: first INTEGER column => B+ Tree indexed).
CREATE TABLE customers (
    id    INT,
    name  VARCHAR(64),
    email VARCHAR(128)
);

-- Orders. order_id is the primary key. customer_id is a logical foreign key to
-- customers.id (relationship enforced by the application; FK constraints are a
-- future enhancement, not part of the v1 SQL subset).
CREATE TABLE orders (
    order_id    INT,
    customer_id INT,
    total_cents BIGINT,
    status      VARCHAR(16)
);

-- Seed data so SELECTs return something immediately.
INSERT INTO customers VALUES (1, 'Ada Lovelace',   'ada@example.com');
INSERT INTO customers VALUES (2, 'Alan Turing',    'alan@example.com');

INSERT INTO orders VALUES (1001, 1, 4999, 'shipped');
INSERT INTO orders VALUES (1002, 1, 1500, 'pending');
INSERT INTO orders VALUES (1003, 2, 9900, 'shipped');
