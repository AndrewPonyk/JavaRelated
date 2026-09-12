-- V3: Idempotent order creation (expand-only, backward compatible: the previous
-- release simply never sets the column).
--
-- A client may send an `Idempotency-Key` header on POST /api/v1/orders; retries
-- with the same key return the original order instead of creating a duplicate.
-- Uniqueness is scoped per customer; the partial index keeps NULLs (no key sent)
-- unconstrained.

ALTER TABLE orders
    ADD COLUMN idempotency_key VARCHAR(64);

CREATE UNIQUE INDEX ux_orders_customer_idempotency
    ON orders (customer_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
