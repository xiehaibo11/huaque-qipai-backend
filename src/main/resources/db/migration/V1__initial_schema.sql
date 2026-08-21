CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE user_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    provider VARCHAR(20) NOT NULL,
    provider_subject VARCHAR(200) NOT NULL,
    phone_number VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_identity_provider_subject UNIQUE (provider, provider_subject)
);

CREATE UNIQUE INDEX uk_identity_phone
    ON user_identities(phone_number)
    WHERE phone_number IS NOT NULL;

CREATE TABLE otp_challenges (
    id UUID PRIMARY KEY,
    phone_number VARCHAR(32) NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL,
    max_attempts INTEGER NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_otp_phone_created
    ON otp_challenges(phone_number, created_at DESC);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    family_id UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by UUID,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_refresh_family ON refresh_tokens(family_id);

CREATE TABLE payment_products (
    id UUID PRIMARY KEY,
    product_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE payment_orders (
    id UUID PRIMARY KEY,
    merchant_order_no VARCHAR(64) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES app_users(id),
    product_id UUID NOT NULL REFERENCES payment_products(id),
    provider VARCHAR(20) NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    provider_order_no VARCHAR(120),
    failure_code VARCHAR(80),
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_order_user_idempotency UNIQUE (user_id, idempotency_key)
);

CREATE UNIQUE INDEX uk_order_provider_number
    ON payment_orders(provider, provider_order_no)
    WHERE provider_order_no IS NOT NULL;

CREATE TABLE payment_webhook_events (
    id UUID PRIMARY KEY,
    provider VARCHAR(20) NOT NULL,
    provider_event_id VARCHAR(160) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    processing_status VARCHAR(20) NOT NULL,
    order_id UUID REFERENCES payment_orders(id),
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    CONSTRAINT uk_webhook_provider_event UNIQUE (provider, provider_event_id)
);

CREATE TABLE payment_outbox (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    CONSTRAINT uk_outbox_event_aggregate UNIQUE (event_type, aggregate_id)
);
