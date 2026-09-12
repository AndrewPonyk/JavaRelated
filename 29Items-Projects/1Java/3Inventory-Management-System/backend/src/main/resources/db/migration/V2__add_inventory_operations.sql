ALTER TABLE warehouses
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6);

ALTER TABLE inventory_items
    ADD COLUMN reserved_quantity BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT ck_inventory_reserved CHECK (reserved_quantity >= 0),
    ADD CONSTRAINT ck_inventory_available CHECK (reserved_quantity <= quantity);

CREATE TABLE item_barcodes (
    id BINARY(16) NOT NULL,
    inventory_item_id BINARY(16) NOT NULL,
    barcode VARCHAR(128) NOT NULL,
    symbology VARCHAR(24) NOT NULL,
    primary_barcode BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_barcode_item FOREIGN KEY (inventory_item_id) REFERENCES inventory_items (id),
    CONSTRAINT uk_item_barcode UNIQUE (inventory_item_id, barcode),
    INDEX ix_barcode_lookup (barcode),
    INDEX ix_barcode_item (inventory_item_id)
);

INSERT INTO item_barcodes (id, inventory_item_id, barcode, symbology, primary_barcode)
SELECT UUID_TO_BIN(UUID()), id, barcode, 'UNKNOWN', TRUE FROM inventory_items;

CREATE TABLE stock_movements (
    id BINARY(16) NOT NULL,
    inventory_item_id BINARY(16) NOT NULL,
    related_item_id BINARY(16) NULL,
    movement_type VARCHAR(32) NOT NULL,
    quantity_delta BIGINT NOT NULL,
    quantity_before BIGINT NOT NULL,
    quantity_after BIGINT NOT NULL,
    reason VARCHAR(250) NOT NULL,
    reference_type VARCHAR(32) NULL,
    reference_id VARCHAR(128) NULL,
    actor VARCHAR(160) NOT NULL,
    idempotency_key VARCHAR(128) NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_movement_item FOREIGN KEY (inventory_item_id) REFERENCES inventory_items (id),
    CONSTRAINT fk_movement_related_item FOREIGN KEY (related_item_id) REFERENCES inventory_items (id),
    INDEX ix_movement_item_time (inventory_item_id, occurred_at),
    INDEX ix_movement_reference (reference_type, reference_id),
    INDEX ix_movement_idempotency (idempotency_key)
);

CREATE TABLE reservations (
    id BINARY(16) NOT NULL,
    inventory_item_id BINARY(16) NOT NULL,
    quantity BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    external_reference VARCHAR(128) NOT NULL,
    reason VARCHAR(250) NOT NULL,
    actor VARCHAR(160) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_reservation_item FOREIGN KEY (inventory_item_id) REFERENCES inventory_items (id),
    CONSTRAINT ck_reservation_quantity CHECK (quantity > 0),
    CONSTRAINT uk_reservation_reference UNIQUE (external_reference),
    INDEX ix_reservation_item_status (inventory_item_id, status)
);

CREATE TABLE idempotency_records (
    idempotency_key VARCHAR(128) NOT NULL,
    operation VARCHAR(80) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    resource_id BINARY(16) NULL,
    response_body JSON NOT NULL,
    http_status INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (idempotency_key),
    INDEX ix_idempotency_expiry (expires_at)
);

CREATE TABLE outbox_events (
    id BINARY(16) NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id BINARY(16) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    topic VARCHAR(160) NOT NULL,
    event_key VARCHAR(128) NOT NULL,
    payload JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at TIMESTAMP(6) NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX ix_outbox_unpublished (published_at, created_at)
);

CREATE TABLE inbox_events (
    event_id BINARY(16) NOT NULL,
    consumer_name VARCHAR(120) NOT NULL,
    processed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (event_id, consumer_name)
);

CREATE TABLE audit_entries (
    id BINARY(16) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id BINARY(16) NOT NULL,
    action VARCHAR(80) NOT NULL,
    actor VARCHAR(160) NOT NULL,
    reason VARCHAR(250) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    before_state JSON NULL,
    after_state JSON NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX ix_audit_entity_time (entity_type, entity_id, occurred_at),
    INDEX ix_audit_correlation (correlation_id)
);

CREATE TABLE forecast_snapshots (
    id BINARY(16) NOT NULL,
    inventory_item_id BINARY(16) NOT NULL,
    horizon_days INT NOT NULL,
    predicted_demand DECIMAL(18, 4) NOT NULL,
    lower_bound DECIMAL(18, 4) NOT NULL,
    upper_bound DECIMAL(18, 4) NOT NULL,
    model_version VARCHAR(120) NOT NULL,
    feature_version VARCHAR(80) NOT NULL,
    model_mae DECIMAL(18, 4) NULL,
    generated_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_forecast_item FOREIGN KEY (inventory_item_id) REFERENCES inventory_items (id),
    INDEX ix_forecast_item_generated (inventory_item_id, generated_at)
);

CREATE TABLE low_stock_alerts (
    id BINARY(16) NOT NULL,
    event_id BINARY(16) NOT NULL,
    inventory_item_id BINARY(16) NOT NULL,
    quantity BIGINT NOT NULL,
    reorder_point BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    acknowledged_at TIMESTAMP(6) NULL,
    acknowledged_by VARCHAR(160) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_alert_item FOREIGN KEY (inventory_item_id) REFERENCES inventory_items (id),
    CONSTRAINT uk_alert_event UNIQUE (event_id),
    INDEX ix_alert_status_time (status, created_at)
);
