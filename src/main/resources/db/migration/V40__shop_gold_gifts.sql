ALTER TABLE shop_products
    ADD COLUMN section VARCHAR(40) NOT NULL DEFAULT 'default';

UPDATE shop_products
SET section = 'value_recommendation'
WHERE category = 'hot_recommendation';

INSERT INTO payment_products (
    id, product_code, name, amount_minor, currency, enabled, created_at, updated_at
) VALUES
    ('00000000-0000-0000-0000-000000000341', 'GOLD_GIFT_6', '6元金币礼包', 600, 'CNY', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000342', 'GOLD_GIFT_18', '18元金币礼包', 1800, 'CNY', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000343', 'GOLD_GIFT_30', '30元金币礼包', 3000, 'CNY', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000344', 'GOLD_GIFT_88', '88元金币礼包', 8800, 'CNY', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (product_code) DO UPDATE SET
    name = EXCLUDED.name,
    amount_minor = EXCLUDED.amount_minor,
    currency = EXCLUDED.currency,
    enabled = EXCLUDED.enabled,
    updated_at = CURRENT_TIMESTAMP;

WITH source (
    id, product_code, display_name, icon_key, price_amount, reward_quantity,
    sort_order, payment_product_code
) AS (
    VALUES
        ('10000000-0000-0000-0000-000000000207'::uuid, 'GOLD_GIFT_6', '6元金币礼包', 'coin_gift', 600::bigint, 78000::bigint, 207, 'GOLD_GIFT_6'),
        ('10000000-0000-0000-0000-000000000208'::uuid, 'GOLD_GIFT_18', '18元金币礼包', 'coin_bag', 1800::bigint, 210000::bigint, 208, 'GOLD_GIFT_18'),
        ('10000000-0000-0000-0000-000000000209'::uuid, 'GOLD_GIFT_30', '30元金币礼包', 'coin_chest', 3000::bigint, 300000::bigint, 209, 'GOLD_GIFT_30'),
        ('10000000-0000-0000-0000-000000000210'::uuid, 'GOLD_GIFT_88', '88元金币礼包', 'treasure_pot', 8800::bigint, 880000::bigint, 210, 'GOLD_GIFT_88')
)
INSERT INTO shop_products (
    id, product_code, category, section, display_name, icon_key, price_currency,
    price_amount, reward_type, reward_quantity, sort_order, enabled,
    daily_limit, lifetime_limit, payment_product_id, created_at, updated_at
)
SELECT
    source.id,
    source.product_code,
    'hot_recommendation',
    'gold_gift',
    source.display_name,
    source.icon_key,
    'CNY',
    source.price_amount,
    'COIN',
    source.reward_quantity,
    source.sort_order,
    TRUE,
    NULL,
    NULL,
    payment_products.id,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM source
JOIN payment_products
    ON payment_products.product_code = source.payment_product_code;

INSERT INTO shop_product_rewards (
    product_id, reward_type, reward_quantity, item_code, grant_order, purchase_number
)
SELECT id, reward_type, reward_quantity, NULL, 1, 0
FROM shop_products
WHERE product_code IN ('GOLD_GIFT_6', 'GOLD_GIFT_18', 'GOLD_GIFT_30', 'GOLD_GIFT_88');
