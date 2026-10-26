CREATE TABLE payments (
    id              UUID PRIMARY KEY,
    order_id        UUID         NOT NULL UNIQUE,
    order_code      VARCHAR(32)  NOT NULL,
    user_id         BIGINT       NOT NULL,
    customer_email  VARCHAR(255),
    amount          BIGINT       NOT NULL CHECK (amount >= 0),
    method          VARCHAR(20)  NOT NULL,
    provider        VARCHAR(20)  NOT NULL,
    status          VARCHAR(30)  NOT NULL,
    txn_ref         VARCHAR(64)  NOT NULL UNIQUE,
    provider_txn_id VARCHAR(100),
    failure_reason  TEXT,
    paid_at         TIMESTAMPTZ,
    refunded_at     TIMESTAMPTZ,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_payments_user ON payments (user_id, created_at DESC);
CREATE INDEX idx_payments_created ON payments (created_at);

-- Immutable audit trail of every interaction with a gateway (used for reconciliation).
CREATE TABLE payment_transactions (
    id          BIGSERIAL PRIMARY KEY,
    payment_id  UUID        NOT NULL REFERENCES payments (id) ON DELETE CASCADE,
    type        VARCHAR(20) NOT NULL,
    status      VARCHAR(20) NOT NULL,
    amount      BIGINT      NOT NULL,
    raw_payload TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payment_tx_payment ON payment_transactions (payment_id);

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
