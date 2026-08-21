CREATE TABLE membership_products (
    product_id UUID PRIMARY KEY REFERENCES payment_products(id),
    product_code VARCHAR(64) NOT NULL UNIQUE REFERENCES payment_products(product_code),
    plan_code VARCHAR(64) NOT NULL UNIQUE,
    duration_days INTEGER NOT NULL CHECK (duration_days > 0),
    gift_value_yuan INTEGER NOT NULL CHECK (gift_value_yuan >= 0),
    price_text VARCHAR(40) NOT NULL,
    day_cost_text VARCHAR(40) NOT NULL,
    card_style VARCHAR(20) NOT NULL,
    corner_tag VARCHAR(20) NOT NULL,
    subscription BOOLEAN NOT NULL,
    privileges_count INTEGER NOT NULL CHECK (privileges_count > 0),
    daily_gift_value_yuan INTEGER NOT NULL CHECK (daily_gift_value_yuan >= 0),
    rewards JSONB NOT NULL,
    sort_order INTEGER NOT NULL UNIQUE,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_membership_product_card_style
        CHECK (card_style IN ('RED', 'GREEN', 'PURPLE')),
    CONSTRAINT chk_membership_product_corner_tag
        CHECK (corner_tag IN ('NONE', 'HOT', 'VALUE'))
);

CREATE TABLE user_memberships (
    user_id UUID PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    membership_level INTEGER NOT NULL CHECK (membership_level >= 0),
    started_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    auto_renew BOOLEAN NOT NULL,
    last_order_id UUID REFERENCES payment_orders(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_user_membership_period CHECK (expires_at > started_at)
);

CREATE INDEX idx_user_memberships_expires_at ON user_memberships(expires_at);

CREATE TABLE membership_reward_grants (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    source_type VARCHAR(40) NOT NULL,
    source_id VARCHAR(120) NOT NULL,
    reward_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    duration_days INTEGER CHECK (duration_days IS NULL OR duration_days > 0),
    metadata JSONB NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_membership_reward_grant UNIQUE (
        user_id, source_type, source_id, reward_code, display_name
    )
);

CREATE INDEX idx_membership_reward_grants_user
    ON membership_reward_grants(user_id, granted_at DESC);

INSERT INTO payment_products (
    id, product_code, name, amount_minor, currency, enabled, created_at, updated_at
) VALUES
    (
        '00000000-0000-0000-0000-000000000201',
        'SXVIP_CONTINUOUS_MONTH',
        '30天会员',
        2800,
        'CNY',
        TRUE,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        '00000000-0000-0000-0000-000000000202',
        'SXVIP_30_DAYS',
        '30天会员',
        3500,
        'CNY',
        TRUE,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        '00000000-0000-0000-0000-000000000203',
        'SXVIP_90_DAYS',
        '90天会员',
        7800,
        'CNY',
        TRUE,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        '00000000-0000-0000-0000-000000000204',
        'SXVIP_365_DAYS',
        '365天会员',
        26800,
        'CNY',
        TRUE,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        '00000000-0000-0000-0000-000000000205',
        'SXVIP_7_DAYS',
        '7天会员',
        2500,
        'CNY',
        TRUE,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
ON CONFLICT (product_code) DO UPDATE SET
    name = EXCLUDED.name,
    amount_minor = EXCLUDED.amount_minor,
    currency = EXCLUDED.currency,
    enabled = EXCLUDED.enabled,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO membership_products (
    product_id,
    product_code,
    plan_code,
    duration_days,
    gift_value_yuan,
    price_text,
    day_cost_text,
    card_style,
    corner_tag,
    subscription,
    privileges_count,
    daily_gift_value_yuan,
    rewards,
    sort_order,
    active,
    created_at,
    updated_at
)
SELECT
    payment_products.id,
    source.product_code,
    source.plan_code,
    source.duration_days,
    source.gift_value_yuan,
    source.price_text,
    source.day_cost_text,
    source.card_style,
    source.corner_tag,
    source.subscription,
    15,
    18,
    source.rewards::jsonb,
    source.sort_order,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM (
    VALUES
        (
            'SXVIP_CONTINUOUS_MONTH',
            'CONTINUOUS_MONTH_30',
            30,
            42,
            '连续包月:28元',
            '每天仅0.9元',
            'RED',
            'HOT',
            TRUE,
            1,
            '[{"code":"COIN","displayName":"金币","quantity":20000,"countText":"x20000","iconKey":"membership_reward_coin"},{"code":"SHUFFLE_TICKET","displayName":"洗牌券","quantity":2,"countText":"x2","iconKey":"membership_reward_shuffle_ticket"},{"code":"LUCK_BEAD","displayName":"转运珠","quantity":2,"countText":"x2","iconKey":"membership_reward_luck_bead"},{"code":"ENTRY_EFFECT","displayName":"入场3天","quantity":3,"countText":"x3","durationDays":3,"iconKey":"membership_reward_entry_ticket"},{"code":"TABLECLOTH","displayName":"桌布3天","quantity":3,"countText":"x3","durationDays":3,"iconKey":"membership_reward_tablecloth"},{"code":"CARD_BACK","displayName":"牌背3天","quantity":3,"countText":"x3","durationDays":3,"iconKey":"membership_reward_card_back"}]'
        ),
        (
            'SXVIP_30_DAYS',
            'FIXED_30',
            30,
            42,
            '35元',
            '每天仅1.1元',
            'GREEN',
            'NONE',
            FALSE,
            2,
            '[{"code":"COIN","displayName":"金币","quantity":20000,"countText":"x20000","iconKey":"membership_reward_coin"},{"code":"SHUFFLE_TICKET","displayName":"洗牌券","quantity":2,"countText":"x2","iconKey":"membership_reward_shuffle_ticket"},{"code":"LUCK_BEAD","displayName":"转运珠","quantity":2,"countText":"x2","iconKey":"membership_reward_luck_bead"},{"code":"ENTRY_EFFECT","displayName":"入场3天","quantity":3,"countText":"x3","durationDays":3,"iconKey":"membership_reward_entry_ticket"},{"code":"TABLECLOTH","displayName":"桌布3天","quantity":3,"countText":"x3","durationDays":3,"iconKey":"membership_reward_tablecloth"},{"code":"CARD_BACK","displayName":"牌背3天","quantity":3,"countText":"x3","durationDays":3,"iconKey":"membership_reward_card_back"}]'
        ),
        (
            'SXVIP_90_DAYS',
            'FIXED_90',
            90,
            136,
            '78元',
            '每天仅0.8元',
            'GREEN',
            'NONE',
            FALSE,
            3,
            '[{"code":"COIN","displayName":"金币","quantity":60000,"countText":"x60000","iconKey":"membership_reward_coin"},{"code":"SHUFFLE_TICKET","displayName":"洗牌券","quantity":6,"countText":"x6","iconKey":"membership_reward_shuffle_ticket"},{"code":"LUCK_BEAD","displayName":"转运珠","quantity":6,"countText":"x6","iconKey":"membership_reward_luck_bead"},{"code":"ENTRY_EFFECT","displayName":"入场10天","quantity":10,"countText":"x10","durationDays":10,"iconKey":"membership_reward_entry_ticket"},{"code":"TABLECLOTH","displayName":"桌布10天","quantity":10,"countText":"x10","durationDays":10,"iconKey":"membership_reward_tablecloth"},{"code":"CARD_BACK","displayName":"牌背10天","quantity":10,"countText":"x10","durationDays":10,"iconKey":"membership_reward_card_back"}]'
        ),
        (
            'SXVIP_365_DAYS',
            'FIXED_365',
            365,
            588,
            '268元',
            '每天仅0.7元',
            'PURPLE',
            'VALUE',
            FALSE,
            4,
            '[{"code":"COIN","displayName":"金币","quantity":300000,"countText":"x300000","iconKey":"membership_reward_coin"},{"code":"SHUFFLE_TICKET","displayName":"洗牌券","quantity":30,"countText":"x30","iconKey":"membership_reward_shuffle_ticket"},{"code":"LUCK_BEAD","displayName":"转运珠","quantity":30,"countText":"x30","iconKey":"membership_reward_luck_bead"},{"code":"ENTRY_EFFECT","displayName":"入场50天","quantity":50,"countText":"x50","durationDays":50,"iconKey":"membership_reward_entry_ticket"},{"code":"TABLECLOTH","displayName":"桌布50天","quantity":50,"countText":"x50","durationDays":50,"iconKey":"membership_reward_tablecloth"},{"code":"CARD_BACK","displayName":"牌背50天","quantity":50,"countText":"x50","durationDays":50,"iconKey":"membership_reward_card_back"}]'
        ),
        (
            'SXVIP_7_DAYS',
            'FIXED_7',
            7,
            12,
            '25元',
            '每天仅3.5元',
            'GREEN',
            'NONE',
            FALSE,
            5,
            '[{"code":"COIN","displayName":"金币","quantity":10000,"countText":"x10000","iconKey":"membership_reward_coin"},{"code":"SHUFFLE_TICKET","displayName":"洗牌券","quantity":1,"countText":"x1","iconKey":"membership_reward_shuffle_ticket"},{"code":"TABLECLOTH","displayName":"桌布1天","quantity":1,"countText":"x1","durationDays":1,"iconKey":"membership_reward_tablecloth"},{"code":"CARD_BACK","displayName":"牌背1天","quantity":1,"countText":"x1","durationDays":1,"iconKey":"membership_reward_card_back"}]'
        )
) AS source (
    product_code,
    plan_code,
    duration_days,
    gift_value_yuan,
    price_text,
    day_cost_text,
    card_style,
    corner_tag,
    subscription,
    sort_order,
    rewards
)
JOIN payment_products ON payment_products.product_code = source.product_code
ON CONFLICT (product_id) DO UPDATE SET
    product_code = EXCLUDED.product_code,
    plan_code = EXCLUDED.plan_code,
    duration_days = EXCLUDED.duration_days,
    gift_value_yuan = EXCLUDED.gift_value_yuan,
    price_text = EXCLUDED.price_text,
    day_cost_text = EXCLUDED.day_cost_text,
    card_style = EXCLUDED.card_style,
    corner_tag = EXCLUDED.corner_tag,
    subscription = EXCLUDED.subscription,
    privileges_count = EXCLUDED.privileges_count,
    daily_gift_value_yuan = EXCLUDED.daily_gift_value_yuan,
    rewards = EXCLUDED.rewards,
    sort_order = EXCLUDED.sort_order,
    active = EXCLUDED.active,
    updated_at = CURRENT_TIMESTAMP;
