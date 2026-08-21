CREATE TABLE membership_daily_gift_claims (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    claimed_on DATE NOT NULL,
    gift_id INTEGER NOT NULL CHECK (gift_id IN (1, 2)),
    rewards JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_membership_daily_gift_claim_user_day UNIQUE (user_id, claimed_on)
);

CREATE INDEX idx_membership_daily_gift_claim_user_created
    ON membership_daily_gift_claims(user_id, created_at DESC);
