-- V2: Indexes for the two real query patterns of the API:
--   * GET /api/v1/orders?customerId=…      → customer lookup
--   * ops/reporting scans by status window  → status + created_at
-- FK join for the @EntityGraph detail fetch → order_items.order_id

CREATE INDEX idx_orders_customer_id ON orders (customer_id);

CREATE INDEX idx_orders_status_created_at ON orders (status, created_at DESC);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
