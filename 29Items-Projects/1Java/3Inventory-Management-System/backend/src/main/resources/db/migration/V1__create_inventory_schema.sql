CREATE TABLE warehouses (
    id BINARY(16) NOT NULL,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_warehouses_code UNIQUE (code)
);

CREATE TABLE inventory_items (
    id BINARY(16) NOT NULL,
    warehouse_id BINARY(16) NOT NULL,
    sku VARCHAR(64) NOT NULL,
    barcode VARCHAR(128) NOT NULL,
    name VARCHAR(160) NOT NULL,
    quantity BIGINT NOT NULL,
    reorder_point BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_inventory_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT uk_inventory_warehouse_sku UNIQUE (warehouse_id, sku),
    CONSTRAINT uk_inventory_warehouse_barcode UNIQUE (warehouse_id, barcode),
    CONSTRAINT ck_inventory_quantity CHECK (quantity >= 0),
    CONSTRAINT ck_inventory_reorder_point CHECK (reorder_point >= 0),
    INDEX ix_inventory_barcode (barcode),
    INDEX ix_inventory_updated_at (updated_at)
);

INSERT INTO warehouses (id, code, name)
VALUES (UUID_TO_BIN('00000000-0000-0000-0000-000000000001'), 'MAIN', 'Main Warehouse');

