-- V1: Order aggregate tables.
-- Rules for this directory (enforced by review, see TECH-NOTES.md §3.6):
--   * migrations are append-only — never edit a version that has been applied anywhere;
--   * every change must be expand/contract compatible with the previously deployed release.

CREATE TABLE orders (
    id           UUID         NOT NULL,
    customer_id  UUID         NOT NULL,
    status       VARCHAR(32)  NOT NULL,
    currency     VARCHAR(3)   NOT NULL DEFAULT 'USD',
    total_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT ck_orders_status CHECK (status IN ('NEW', 'CONFIRMED', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT ck_orders_total_amount CHECK (total_amount >= 0)
);

CREATE TABLE order_items (
    id           UUID           NOT NULL,
    order_id     UUID           NOT NULL,
    sku          VARCHAR(64)    NOT NULL,
    product_name VARCHAR(255)   NOT NULL,
    quantity     INTEGER        NOT NULL,
    unit_price   NUMERIC(19, 4) NOT NULL,
    CONSTRAINT pk_order_items PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_unit_price CHECK (unit_price >= 0)
);
