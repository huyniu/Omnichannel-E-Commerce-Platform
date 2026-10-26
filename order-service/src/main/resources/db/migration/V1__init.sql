-- ===== Inventory (real-time stock) =====
-- available: can still be sold; reserved: held by PENDING orders; sold: committed by paid/confirmed orders.
CREATE TABLE inventory (
    sku        VARCHAR(64) PRIMARY KEY,
    available  INT         NOT NULL CHECK (available >= 0),
    reserved   INT         NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    sold       INT         NOT NULL DEFAULT 0 CHECK (sold >= 0),
    version    BIGINT      NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ===== Orders =====
CREATE TABLE orders (
    id               UUID PRIMARY KEY,
    code             VARCHAR(32)  NOT NULL UNIQUE,
    user_id          BIGINT       NOT NULL,
    customer_email   VARCHAR(255),
    status           VARCHAR(20)  NOT NULL,
    payment_method   VARCHAR(20)  NOT NULL,
    payment_status   VARCHAR(20)  NOT NULL,
    subtotal         BIGINT       NOT NULL,
    shipping_fee     BIGINT       NOT NULL DEFAULT 0,
    total_amount     BIGINT       NOT NULL,
    shipping_name    VARCHAR(255) NOT NULL,
    shipping_phone   VARCHAR(20)  NOT NULL,
    shipping_address TEXT         NOT NULL,
    note             TEXT,
    cancel_reason    TEXT,
    idempotency_key  VARCHAR(100),
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_orders_idempotency UNIQUE (user_id, idempotency_key)
);
CREATE INDEX idx_orders_user ON orders (user_id, created_at DESC);
CREATE INDEX idx_orders_status_created ON orders (status, created_at);

-- Product data is a snapshot taken at purchase time (bounded context: Order).
CREATE TABLE order_items (
    id           BIGSERIAL PRIMARY KEY,
    order_id     UUID         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    sku          VARCHAR(64)  NOT NULL,
    product_id   VARCHAR(64)  NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    variant_name VARCHAR(255),
    image_url    TEXT,
    unit_price   BIGINT       NOT NULL,
    quantity     INT          NOT NULL CHECK (quantity > 0),
    line_total   BIGINT       NOT NULL
);
CREATE INDEX idx_order_items_order ON order_items (order_id);

CREATE TABLE order_status_history (
    id          BIGSERIAL PRIMARY KEY,
    order_id    UUID        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status   VARCHAR(20) NOT NULL,
    reason      TEXT,
    changed_by  VARCHAR(64),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_order_history_order ON order_status_history (order_id);

-- One row per (order, sku): lets compensation know exactly what to give back.
CREATE TABLE stock_reservations (
    id         BIGSERIAL PRIMARY KEY,
    order_id   UUID        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    sku        VARCHAR(64) NOT NULL,
    quantity   INT         NOT NULL CHECK (quantity > 0),
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_reservation UNIQUE (order_id, sku)
);

-- ===== Transactional outbox & consumer idempotency =====
CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   VARCHAR(64)  NOT NULL,
    event_type     VARCHAR(50)  NOT NULL,
    topic          VARCHAR(100) NOT NULL,
    payload        TEXT         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    attempts       INT          NOT NULL DEFAULT 0,
    last_error     TEXT
);
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;

CREATE TABLE processed_events (
    event_id     VARCHAR(64) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
