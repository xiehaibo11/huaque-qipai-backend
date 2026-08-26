CREATE TABLE gold_membership_cards (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    product_code VARCHAR(64) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_gold_membership_card_user_product
        UNIQUE (user_id, product_code),
    CONSTRAINT ck_gold_membership_card_product
        CHECK (product_code IN ('GOLD_MEMBER_WEEK', 'GOLD_MEMBER_MONTH')),
    CONSTRAINT ck_gold_membership_card_period
        CHECK (expires_at > started_at)
);

CREATE INDEX idx_gold_membership_cards_user_expiry
    ON gold_membership_cards (user_id, expires_at DESC);

CREATE TABLE gold_membership_card_claims (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    product_code VARCHAR(64) NOT NULL,
    claimed_on DATE NOT NULL,
    coins BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_gold_membership_claim_user_product_day
        UNIQUE (user_id, product_code, claimed_on),
    CONSTRAINT ck_gold_membership_claim_product
        CHECK (product_code IN ('GOLD_MEMBER_WEEK', 'GOLD_MEMBER_MONTH')),
    CONSTRAINT ck_gold_membership_claim_coins CHECK (coins > 0)
);

CREATE INDEX idx_gold_membership_claims_user_day
    ON gold_membership_card_claims (user_id, claimed_on DESC);
