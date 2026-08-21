ALTER TABLE player_wallets
    ADD COLUMN coupons BIGINT NOT NULL DEFAULT 0;

ALTER TABLE player_wallets
    ADD CONSTRAINT ck_player_wallets_coupons_nonnegative CHECK (coupons >= 0);

CREATE TABLE shop_products (
    id UUID PRIMARY KEY,
    product_code VARCHAR(64) NOT NULL UNIQUE,
    category VARCHAR(40) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    icon_key VARCHAR(120) NOT NULL,
    price_currency VARCHAR(16) NOT NULL,
    price_amount BIGINT NOT NULL CHECK (price_amount >= 0),
    reward_type VARCHAR(40) NOT NULL,
    reward_quantity BIGINT NOT NULL CHECK (reward_quantity > 0),
    sort_order INTEGER NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL,
    daily_limit INTEGER CHECK (daily_limit IS NULL OR daily_limit > 0),
    lifetime_limit INTEGER CHECK (lifetime_limit IS NULL OR lifetime_limit > 0),
    payment_product_id UUID REFERENCES payment_products(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_shop_price_currency
        CHECK (price_currency IN ('CNY', 'DIAMOND', 'COUPON', 'FREE')),
    CONSTRAINT ck_shop_free_price
        CHECK (price_currency <> 'FREE' OR price_amount = 0),
    CONSTRAINT ck_shop_payment_link
        CHECK ((price_currency = 'CNY') = (payment_product_id IS NOT NULL))
);

CREATE INDEX idx_shop_products_category_sort
    ON shop_products(category, sort_order) WHERE enabled;

CREATE TABLE shop_product_rewards (
    id BIGSERIAL PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES shop_products(id) ON DELETE CASCADE,
    reward_type VARCHAR(40) NOT NULL,
    reward_quantity BIGINT NOT NULL CHECK (reward_quantity > 0),
    item_code VARCHAR(64),
    grant_order INTEGER NOT NULL CHECK (grant_order > 0),
    purchase_number INTEGER NOT NULL DEFAULT 0 CHECK (purchase_number >= 0),
    CONSTRAINT uk_shop_product_reward_order
        UNIQUE (product_id, purchase_number, grant_order)
);

CREATE TABLE shop_purchase_records (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES shop_products(id),
    product_code VARCHAR(64) NOT NULL,
    order_id UUID REFERENCES payment_orders(id),
    idempotency_key VARCHAR(120) NOT NULL,
    price_currency VARCHAR(16) NOT NULL,
    price_amount BIGINT NOT NULL CHECK (price_amount >= 0),
    reward_type VARCHAR(40) NOT NULL,
    reward_quantity BIGINT NOT NULL CHECK (reward_quantity > 0),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_shop_purchase_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT uk_shop_purchase_order UNIQUE (order_id),
    CONSTRAINT ck_shop_purchase_status CHECK (status IN ('FULFILLED'))
);

CREATE INDEX idx_shop_purchase_user_created
    ON shop_purchase_records(user_id, created_at DESC);
CREATE INDEX idx_shop_purchase_daily_limit
    ON shop_purchase_records(user_id, product_id, created_at DESC);

CREATE TABLE shop_inventory_items (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    item_code VARCHAR(64) NOT NULL,
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_shop_inventory_user_item UNIQUE (user_id, item_code)
);

INSERT INTO payment_products (
    id, product_code, name, amount_minor, currency, enabled, created_at, updated_at
) VALUES
    ('00000000-0000-0000-0000-000000000311', 'HOT_FIRST_RECHARGE', '首充礼包', 600, 'CNY', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000312', 'HOT_DAILY_GIFT', '每日礼包', 600, 'CNY', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000313', 'HOT_WEEK_GIFT', '每周礼包', 1800, 'CNY', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000314', 'HOT_MONTH_GIFT', '每月礼包', 4800, 'CNY', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000315', 'HOT_VALUE_MONTH_CARD', '超值月卡', 2800, 'CNY', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000320', 'GOLD_MEMBER_VALUE_MONTH', '金币超值月卡', 2800, 'CNY', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000330', 'DIAMOND_100', '100钻石', 100, 'CNY', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000331', 'DIAMOND_300', '300钻石', 300, 'CNY', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000332', 'DIAMOND_600', '600钻石', 600, 'CNY', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000333', 'DIAMOND_1800', '1800钻石', 1800, 'CNY', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000334', 'DIAMOND_3000', '3000钻石', 3000, 'CNY', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000335', 'DIAMOND_6800', '6800钻石', 6800, 'CNY', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000336', 'DIAMOND_9800', '9800钻石', 9800, 'CNY', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000337', 'DIAMOND_12800', '12800钻石', 12800, 'CNY', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (product_code) DO UPDATE SET
    name = EXCLUDED.name,
    amount_minor = EXCLUDED.amount_minor,
    currency = EXCLUDED.currency,
    enabled = EXCLUDED.enabled,
    updated_at = CURRENT_TIMESTAMP;

WITH source (
    id, product_code, category, display_name, icon_key, price_currency,
    price_amount, reward_type, reward_quantity, sort_order, daily_limit,
    payment_product_code
) AS (
    VALUES
    ('10000000-0000-0000-0000-000000000101'::uuid, 'SXVIP_CONTINUOUS_MONTH', 'time_membership', '连续包月30天', 'vip_gift', 'CNY', 2800, 'MEMBERSHIP_DAY', 30, 101, NULL::integer, 'SXVIP_CONTINUOUS_MONTH'),
    ('10000000-0000-0000-0000-000000000102'::uuid, 'SXVIP_30_DAYS', 'time_membership', '普通会员30天', 'vip_gift', 'CNY', 3500, 'MEMBERSHIP_DAY', 30, 102, NULL::integer, 'SXVIP_30_DAYS'),
    ('10000000-0000-0000-0000-000000000103'::uuid, 'SXVIP_90_DAYS', 'time_membership', '普通会员90天', 'vip_gift', 'CNY', 7800, 'MEMBERSHIP_DAY', 90, 103, NULL::integer, 'SXVIP_90_DAYS'),
    ('10000000-0000-0000-0000-000000000104'::uuid, 'SXVIP_365_DAYS', 'time_membership', '普通会员365天', 'vip_gift', 'CNY', 26800, 'MEMBERSHIP_DAY', 365, 104, NULL::integer, 'SXVIP_365_DAYS'),
    ('10000000-0000-0000-0000-000000000201'::uuid, 'HOT_FIRST_RECHARGE', 'hot_recommendation', '首充礼包', 'vip_gift', 'CNY', 600, 'DIAMOND', 100, 201, NULL::integer, 'HOT_FIRST_RECHARGE'),
    ('10000000-0000-0000-0000-000000000202'::uuid, 'HOT_DAILY_BENEFIT', 'hot_recommendation', '每日福利', 'daily_gift', 'FREE', 0, 'INTERACTION_PROP', 10, 202, 1, NULL),
    ('10000000-0000-0000-0000-000000000203'::uuid, 'HOT_DAILY_GIFT', 'hot_recommendation', '每日礼包', 'coin_gift', 'CNY', 600, 'COIN', 78000, 203, 3, 'HOT_DAILY_GIFT'),
    ('10000000-0000-0000-0000-000000000204'::uuid, 'HOT_WEEK_GIFT', 'hot_recommendation', '每周礼包', 'coin_bag', 'CNY', 1800, 'COIN', 210000, 204, NULL::integer, 'HOT_WEEK_GIFT'),
    ('10000000-0000-0000-0000-000000000205'::uuid, 'HOT_MONTH_GIFT', 'hot_recommendation', '每月礼包', 'coin_chest', 'CNY', 4800, 'COIN', 480000, 205, NULL::integer, 'HOT_MONTH_GIFT'),
    ('10000000-0000-0000-0000-000000000206'::uuid, 'HOT_VALUE_MONTH_CARD', 'hot_recommendation', '超值月卡', 'treasure_pot', 'CNY', 2800, 'COIN', 376000, 206, NULL::integer, 'HOT_VALUE_MONTH_CARD'),
    ('10000000-0000-0000-0000-000000000301'::uuid, 'DIAMOND_100', 'diamond_recharge', '100钻石', 'diamond', 'CNY', 100, 'DIAMOND', 100, 301, NULL::integer, 'DIAMOND_100'),
    ('10000000-0000-0000-0000-000000000302'::uuid, 'DIAMOND_300', 'diamond_recharge', '300钻石', 'diamond', 'CNY', 300, 'DIAMOND', 300, 302, NULL::integer, 'DIAMOND_300'),
    ('10000000-0000-0000-0000-000000000303'::uuid, 'DIAMOND_600', 'diamond_recharge', '600钻石', 'diamond', 'CNY', 600, 'DIAMOND', 600, 303, NULL::integer, 'DIAMOND_600'),
    ('10000000-0000-0000-0000-000000000304'::uuid, 'DIAMOND_1800', 'diamond_recharge', '1800钻石', 'diamond', 'CNY', 1800, 'DIAMOND', 1800, 304, NULL::integer, 'DIAMOND_1800'),
    ('10000000-0000-0000-0000-000000000305'::uuid, 'DIAMOND_3000', 'diamond_recharge', '3000钻石', 'diamond', 'CNY', 3000, 'DIAMOND', 3000, 305, NULL::integer, 'DIAMOND_3000'),
    ('10000000-0000-0000-0000-000000000306'::uuid, 'DIAMOND_6800', 'diamond_recharge', '6800钻石', 'diamond', 'CNY', 6800, 'DIAMOND', 6800, 306, NULL::integer, 'DIAMOND_6800'),
    ('10000000-0000-0000-0000-000000000307'::uuid, 'DIAMOND_9800', 'diamond_recharge', '9800钻石', 'diamond', 'CNY', 9800, 'DIAMOND', 9800, 307, NULL::integer, 'DIAMOND_9800'),
    ('10000000-0000-0000-0000-000000000308'::uuid, 'DIAMOND_12800', 'diamond_recharge', '12800钻石', 'diamond', 'CNY', 12800, 'DIAMOND', 12800, 308, NULL::integer, 'DIAMOND_12800'),
    ('10000000-0000-0000-0000-000000000401'::uuid, 'ROOM_CARD_1', 'room_card', '1房卡', 'room_card', 'DIAMOND', 400, 'ROOM_CARD', 1, 401, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000402'::uuid, 'ROOM_CARD_5', 'room_card', '5房卡', 'room_card', 'DIAMOND', 600, 'ROOM_CARD', 5, 402, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000403'::uuid, 'ROOM_CARD_17', 'room_card', '17房卡', 'room_card', 'DIAMOND', 1800, 'ROOM_CARD', 17, 403, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000404'::uuid, 'ROOM_CARD_29', 'room_card', '29房卡', 'room_card', 'DIAMOND', 3000, 'ROOM_CARD', 29, 404, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000405'::uuid, 'ROOM_CARD_67', 'room_card', '67房卡', 'room_card', 'DIAMOND', 6800, 'ROOM_CARD', 67, 405, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000406'::uuid, 'ROOM_CARD_128', 'room_card', '128房卡', 'room_card', 'DIAMOND', 12800, 'ROOM_CARD', 128, 406, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000501'::uuid, 'COIN_60000', 'coin', '6万金币', 'coin_stack', 'DIAMOND', 600, 'COIN', 60000, 501, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000502'::uuid, 'COIN_300000', 'coin', '30万金币', 'coin_bag', 'DIAMOND', 3000, 'COIN', 300000, 502, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000503'::uuid, 'COIN_880000', 'coin', '88万金币', 'coin_chest', 'DIAMOND', 8800, 'COIN', 880000, 503, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000504'::uuid, 'COIN_1880000', 'coin', '188万金币', 'coin_chest', 'DIAMOND', 18800, 'COIN', 1880000, 504, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000505'::uuid, 'COIN_5180000', 'coin', '518万金币', 'treasure_pot', 'DIAMOND', 51800, 'COIN', 5180000, 505, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000506'::uuid, 'COIN_6480000', 'coin', '648万金币', 'treasure_pot', 'DIAMOND', 64800, 'COIN', 6480000, 506, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000507'::uuid, 'COIN_9280000', 'coin', '928万金币', 'treasure_pot', 'DIAMOND', 92800, 'COIN', 9280000, 507, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000601'::uuid, 'GOLD_MEMBER_WEEK', 'gold_membership', '会员周卡', 'coin_gift', 'DIAMOND', 1800, 'GOLD_MEMBERSHIP_DAY', 7, 601, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000602'::uuid, 'GOLD_MEMBER_MONTH', 'gold_membership', '会员月卡', 'coin_bag', 'DIAMOND', 4800, 'GOLD_MEMBERSHIP_DAY', 30, 602, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000603'::uuid, 'GOLD_MEMBER_VALUE_MONTH', 'gold_membership', '超值月卡', 'treasure_pot', 'CNY', 2800, 'GOLD_MEMBERSHIP_DAY', 30, 603, NULL::integer, 'GOLD_MEMBER_VALUE_MONTH'),
    ('10000000-0000-0000-0000-000000000701'::uuid, 'PROP_GOLD_CARD_1', 'prop', '黄金卡1张', 'coupon_gold', 'DIAMOND', 600, 'INVENTORY_PROP', 1, 701, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000702'::uuid, 'PROP_GOLD_CARD_5', 'prop', '黄金卡5张', 'coupon_gold', 'DIAMOND', 2500, 'INVENTORY_PROP', 5, 702, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000703'::uuid, 'PROP_GOLD_CARD_10', 'prop', '黄金卡10张', 'coupon_gold', 'DIAMOND', 5000, 'INVENTORY_PROP', 10, 703, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000704'::uuid, 'PROP_BLACK_CARD_1', 'prop', '黑钻卡1张', 'coupon_black', 'DIAMOND', 3000, 'INVENTORY_PROP', 1, 704, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000705'::uuid, 'PROP_BLACK_CARD_5', 'prop', '黑钻卡5张', 'coupon_black', 'DIAMOND', 12500, 'INVENTORY_PROP', 5, 705, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000706'::uuid, 'PROP_BLACK_CARD_10', 'prop', '黑钻卡10张', 'coupon_black', 'DIAMOND', 25000, 'INVENTORY_PROP', 10, 706, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000801'::uuid, 'INTERACTION_THUMB', 'interaction', '点赞', 'thumb', 'DIAMOND', 30, 'INTERACTION_PROP', 1, 801, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000802'::uuid, 'INTERACTION_TOAST', 'interaction', '碰杯', 'face', 'DIAMOND', 30, 'INTERACTION_PROP', 1, 802, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000803'::uuid, 'INTERACTION_HANDSHAKE', 'interaction', '握手', 'face', 'DIAMOND', 30, 'INTERACTION_PROP', 1, 803, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000804'::uuid, 'INTERACTION_ICE_BUCKET', 'interaction', '冰桶', 'wash_card', 'DIAMOND', 30, 'INTERACTION_PROP', 1, 804, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000805'::uuid, 'INTERACTION_BOMB', 'interaction', '炸弹', 'face', 'DIAMOND', 30, 'INTERACTION_PROP', 1, 805, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000806'::uuid, 'INTERACTION_MACHINE_GUN', 'interaction', '机关枪', 'voice', 'DIAMOND', 30, 'INTERACTION_PROP', 1, 806, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000807'::uuid, 'INTERACTION_SLIPPER', 'interaction', '拖鞋', 'slipper', 'DIAMOND', 50, 'INTERACTION_PROP', 1, 807, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000808'::uuid, 'INTERACTION_ROSE', 'interaction', '玫瑰', 'rose', 'DIAMOND', 50, 'INTERACTION_PROP', 1, 808, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000901'::uuid, 'DECORATION_TABLE_1', 'decoration', '二八大杠7天', 'tablecloth', 'DIAMOND', 300, 'DECORATION_PROP', 7, 901, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000902'::uuid, 'DECORATION_TABLE_2', 'decoration', '北欧翡翠7天', 'tablecloth', 'DIAMOND', 1500, 'DECORATION_PROP', 7, 902, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000903'::uuid, 'DECORATION_TABLE_3', 'decoration', '暗夜精灵7天', 'card_back', 'DIAMOND', 1500, 'DECORATION_PROP', 7, 903, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000904'::uuid, 'DECORATION_TABLE_4', 'decoration', '冰蓝狂想7天', 'tablecloth', 'DIAMOND', 1500, 'DECORATION_PROP', 7, 904, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000905'::uuid, 'DECORATION_TABLE_5', 'decoration', '红色疾风7天', 'card_back', 'DIAMOND', 1500, 'DECORATION_PROP', 7, 905, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000906'::uuid, 'DECORATION_TABLE_6', 'decoration', '极速幻影7天', 'avatar_frame', 'DIAMOND', 1500, 'DECORATION_PROP', 7, 906, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000907'::uuid, 'DECORATION_TABLE_7', 'decoration', '欧马风情7天', 'tablecloth', 'DIAMOND', 1500, 'DECORATION_PROP', 7, 907, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000908'::uuid, 'DECORATION_TABLE_8', 'decoration', '英伦领袖7天', 'avatar_frame', 'DIAMOND', 1500, 'DECORATION_PROP', 7, 908, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000000909'::uuid, 'DECORATION_TABLE_9', 'decoration', '越野豪杰7天', 'press_bull', 'DIAMOND', 1500, 'DECORATION_PROP', 7, 909, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000001001'::uuid, 'COUPON_ROOM_CARD_1', 'coupon_store', '1房卡', 'room_card', 'COUPON', 120, 'ROOM_CARD', 1, 1001, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000001002'::uuid, 'COUPON_ROOM_CARD_10', 'coupon_store', '10房卡', 'room_card', 'COUPON', 1100, 'ROOM_CARD', 10, 1002, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000001003'::uuid, 'COUPON_ROOM_CARD_30', 'coupon_store', '30房卡', 'room_card', 'COUPON', 3000, 'ROOM_CARD', 30, 1003, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000001004'::uuid, 'COUPON_ROOM_CARD_50', 'coupon_store', '50房卡', 'room_card', 'COUPON', 4800, 'ROOM_CARD', 50, 1004, NULL::integer, NULL),
    ('10000000-0000-0000-0000-000000001005'::uuid, 'COUPON_COIN_10000', 'coupon_store', '1万金币', 'coin_stack', 'COUPON', 150, 'COIN', 10000, 1005, NULL::integer, NULL)
)
INSERT INTO shop_products (
    id, product_code, category, display_name, icon_key, price_currency,
    price_amount, reward_type, reward_quantity, sort_order, enabled,
    daily_limit, lifetime_limit, payment_product_id, created_at, updated_at
)
SELECT
    source.id,
    source.product_code,
    source.category,
    source.display_name,
    source.icon_key,
    source.price_currency,
    source.price_amount,
    source.reward_type,
    source.reward_quantity,
    source.sort_order,
    TRUE,
    source.daily_limit,
    CASE WHEN source.product_code = 'HOT_FIRST_RECHARGE' THEN 1 ELSE NULL END,
    payment_products.id,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM source
LEFT JOIN payment_products
    ON payment_products.product_code = source.payment_product_code;

INSERT INTO shop_product_rewards (
    product_id, reward_type, reward_quantity, item_code, grant_order, purchase_number
)
SELECT
    id, reward_type, reward_quantity, NULL, 1, 0
FROM shop_products
WHERE product_code NOT IN (
    'HOT_FIRST_RECHARGE', 'HOT_DAILY_BENEFIT', 'HOT_DAILY_GIFT'
);

INSERT INTO shop_product_rewards (
    product_id, reward_type, reward_quantity, item_code, grant_order, purchase_number
)
SELECT
    shop_products.id,
    reward_source.reward_type,
    reward_source.reward_quantity,
    reward_source.item_code,
    reward_source.grant_order,
    reward_source.purchase_number
FROM (
    VALUES
        ('HOT_FIRST_RECHARGE', 'DIAMOND', 100::bigint, NULL, 1, 0),
        ('HOT_FIRST_RECHARGE', 'COIN', 20000::bigint, NULL, 2, 0),
        ('HOT_FIRST_RECHARGE', 'INVENTORY_PROP', 1::bigint, 'SHOP_RECORDER_DAY', 3, 0),
        ('HOT_DAILY_BENEFIT', 'INTERACTION_PROP', 10::bigint, 'INTERACTION_ROSE', 1, 0),
        ('HOT_DAILY_GIFT', 'COIN', 78000::bigint, NULL, 1, 1),
        ('HOT_DAILY_GIFT', 'COIN', 90000::bigint, NULL, 1, 2),
        ('HOT_DAILY_GIFT', 'COIN', 96000::bigint, NULL, 1, 3)
) AS reward_source(
    product_code, reward_type, reward_quantity, item_code, grant_order, purchase_number
)
JOIN shop_products ON shop_products.product_code = reward_source.product_code;
