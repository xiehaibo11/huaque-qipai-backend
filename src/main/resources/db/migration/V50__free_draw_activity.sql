CREATE TABLE free_draw_activities (
    id UUID PRIMARY KEY,
    activity_code VARCHAR(64) NOT NULL UNIQUE,
    ad_placement_id VARCHAR(64) NOT NULL,
    provider_source_id VARCHAR(64) NOT NULL,
    daily_limit INTEGER NOT NULL CHECK (daily_limit > 0),
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE free_draw_prizes (
    id UUID PRIMARY KEY,
    activity_id UUID NOT NULL REFERENCES free_draw_activities(id) ON DELETE RESTRICT,
    reward_type VARCHAR(32) NOT NULL CHECK (reward_type IN ('COIN', 'DIAMOND')),
    reward_amount BIGINT NOT NULL CHECK (reward_amount > 0),
    display_name VARCHAR(64) NOT NULL,
    icon_key VARCHAR(32) NOT NULL,
    weight INTEGER NOT NULL CHECK (weight > 0),
    display_order INTEGER NOT NULL CHECK (display_order > 0),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (activity_id, display_order)
);

CREATE TABLE free_draw_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    activity_id UUID NOT NULL REFERENCES free_draw_activities(id) ON DELETE RESTRICT,
    draw_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'GRANTED')),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    granted_at TIMESTAMPTZ,
    reward_prize_id UUID REFERENCES free_draw_prizes(id) ON DELETE RESTRICT,
    reward_type VARCHAR(32),
    reward_amount BIGINT,
    reward_name VARCHAR(64),
    reward_icon_key VARCHAR(32),
    ad_source_id VARCHAR(128),
    ad_show_id VARCHAR(128),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_free_draw_user_day
    ON free_draw_sessions(user_id, activity_id, draw_date, status);

INSERT INTO free_draw_activities (
    id, activity_code, ad_placement_id, provider_source_id, daily_limit, enabled
) VALUES (
    '78306d04-fb4c-4a12-a7e6-596100ff5a01',
    'DAILY_AD_DRAW',
    'b5f8ceca962d11',
    'CSJ:945592324',
    8,
    TRUE
);

INSERT INTO free_draw_prizes (
    id, activity_id, reward_type, reward_amount,
    display_name, icon_key, weight, display_order, enabled
) VALUES
    ('78306d04-fb4c-4a12-a7e6-596100ff5a11',
     '78306d04-fb4c-4a12-a7e6-596100ff5a01', 'COIN', 88,
     '88金币', 'coin_bag', 500, 1, TRUE),
    ('78306d04-fb4c-4a12-a7e6-596100ff5a12',
     '78306d04-fb4c-4a12-a7e6-596100ff5a01', 'COIN', 588,
     '588金币', 'coin_bag', 260, 2, TRUE),
    ('78306d04-fb4c-4a12-a7e6-596100ff5a13',
     '78306d04-fb4c-4a12-a7e6-596100ff5a01', 'DIAMOND', 10,
     '10钻石', 'diamond', 120, 3, TRUE),
    ('78306d04-fb4c-4a12-a7e6-596100ff5a14',
     '78306d04-fb4c-4a12-a7e6-596100ff5a01', 'COIN', 888,
     '888金币', 'coin_bag', 100, 4, TRUE),
    ('78306d04-fb4c-4a12-a7e6-596100ff5a15',
     '78306d04-fb4c-4a12-a7e6-596100ff5a01', 'DIAMOND', 20,
     '20钻石', 'diamond', 19, 5, TRUE),
    ('78306d04-fb4c-4a12-a7e6-596100ff5a16',
     '78306d04-fb4c-4a12-a7e6-596100ff5a01', 'COIN', 100000,
     '10万金币', 'coin_bag', 1, 6, TRUE);
