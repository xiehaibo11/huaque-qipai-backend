CREATE TABLE real_name_verifications (
    user_id UUID PRIMARY KEY REFERENCES app_users(id),
    real_name_masked VARCHAR(64) NOT NULL,
    id_card_hmac VARCHAR(64) NOT NULL,
    id_card_masked VARCHAR(32) NOT NULL,
    birth_date DATE NOT NULL,
    source VARCHAR(16) NOT NULL,
    verified_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_real_name_source
        CHECK (source IN ('MANUAL', 'ALIPAY'))
);

CREATE UNIQUE INDEX uk_real_name_id_card_hmac
    ON real_name_verifications(id_card_hmac);

CREATE TABLE real_name_failed_attempts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    failed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_real_name_failed_attempts_user
    ON real_name_failed_attempts(user_id, failed_at);
