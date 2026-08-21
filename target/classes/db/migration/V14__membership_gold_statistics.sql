CREATE TABLE membership_gold_statistics (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    game_id BIGINT NOT NULL,
    played_on DATE NOT NULL,
    fight_count INTEGER NOT NULL CHECK (fight_count >= 0),
    win_count INTEGER NOT NULL CHECK (win_count >= 0 AND win_count <= fight_count),
    coin_delta BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_membership_gold_statistics_user_game_day
        UNIQUE (user_id, game_id, played_on)
);

CREATE INDEX idx_membership_gold_statistics_user_day
    ON membership_gold_statistics(user_id, played_on DESC);

CREATE INDEX idx_membership_gold_statistics_user_game_day
    ON membership_gold_statistics(user_id, game_id, played_on DESC);
