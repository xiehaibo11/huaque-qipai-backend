UPDATE shop_products
SET section = 'prop_emoji',
    updated_at = CURRENT_TIMESTAMP
WHERE category = 'interaction';

INSERT INTO shop_products (
    id,
    product_code,
    category,
    section,
    display_name,
    icon_key,
    price_currency,
    price_amount,
    reward_type,
    reward_quantity,
    sort_order,
    enabled,
    daily_limit,
    lifetime_limit,
    payment_product_id,
    created_at,
    updated_at
) VALUES (
    '10000000-0000-0000-0000-000000000809'::uuid,
    'CHAT_VOICE_XIAOGU_1_DAY',
    'interaction',
    'yuyin',
    '小谷专属语音包1天',
    'voice',
    'DIAMOND',
    100,
    'INTERACTION_PROP',
    1,
    809,
    TRUE,
    NULL,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO shop_product_rewards (
    product_id,
    reward_type,
    reward_quantity,
    item_code,
    grant_order,
    purchase_number
)
SELECT
    id,
    'INTERACTION_PROP',
    1,
    'PROP_CHAT_VOICE_120404',
    1,
    0
FROM shop_products
WHERE product_code = 'CHAT_VOICE_XIAOGU_1_DAY';
