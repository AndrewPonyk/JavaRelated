CREATE INDEX ix_inventory_warehouse_active_sku
    ON inventory_items (warehouse_id, active, sku);

CREATE INDEX ix_inventory_active_updated
    ON inventory_items (active, updated_at);

CREATE INDEX ix_outbox_dispatch
    ON outbox_events (published_at, attempts, created_at);
