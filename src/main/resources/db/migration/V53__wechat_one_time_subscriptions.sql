CREATE TABLE wechat_subscription_grants (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    template_id VARCHAR(128) NOT NULL,
    scene INTEGER NOT NULL,
    reserved_hash CHAR(64) NOT NULL UNIQUE,
    openid_subject_hash CHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    claimed_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_wechat_subscription_grant_status CHECK (
        status IN (
            'PENDING', 'AVAILABLE', 'DENIED', 'CANCELLED',
            'EXPIRED', 'CLAIMED', 'SENT', 'TERMINAL', 'INVALIDATED'
        )
    )
);

CREATE INDEX idx_wechat_subscription_grants_available
    ON wechat_subscription_grants(
        user_id, template_id, scene, status, confirmed_at, id
    );

CREATE TABLE wechat_subscription_deliveries (
    id UUID PRIMARY KEY,
    grant_id UUID NOT NULL UNIQUE
        REFERENCES wechat_subscription_grants(id),
    user_id UUID NOT NULL REFERENCES app_users(id),
    template_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_id VARCHAR(160) NOT NULL,
    title VARCHAR(15) NOT NULL,
    content VARCHAR(200) NOT NULL,
    target_url VARCHAR(500),
    status VARCHAR(24) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_provider_code INTEGER,
    last_failure_class VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_wechat_subscription_delivery_event
        UNIQUE (user_id, template_id, event_type, event_id),
    CONSTRAINT ck_wechat_subscription_delivery_status CHECK (
        status IN (
            'PENDING', 'SENDING', 'RETRYABLE', 'SENT',
            'TERMINAL', 'AMBIGUOUS'
        )
    ),
    CONSTRAINT ck_wechat_subscription_delivery_attempts
        CHECK (attempts >= 0),
    CONSTRAINT ck_wechat_subscription_delivery_title
        CHECK (char_length(title) BETWEEN 1 AND 15),
    CONSTRAINT ck_wechat_subscription_delivery_content
        CHECK (char_length(content) BETWEEN 1 AND 200)
);

CREATE INDEX idx_wechat_subscription_deliveries_pending
    ON wechat_subscription_deliveries(status, next_attempt_at, created_at, id);
