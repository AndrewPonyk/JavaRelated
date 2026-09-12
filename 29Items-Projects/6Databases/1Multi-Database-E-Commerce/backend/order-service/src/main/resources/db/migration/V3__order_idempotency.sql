-- ============================================================================
-- V3: idempotency key for POST /orders (prevents double-charge on client retry).
-- A NULL key is allowed (unkeyed requests); non-null keys are unique.
-- ============================================================================

ALTER TABLE ORDERS ADD (IDEMPOTENCY_KEY VARCHAR2(64));

CREATE UNIQUE INDEX UX_ORDERS_IDEMPOTENCY ON ORDERS (IDEMPOTENCY_KEY);
