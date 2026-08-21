ALTER TABLE shop_products
    DROP CONSTRAINT ck_shop_price_currency;

ALTER TABLE shop_products
    ADD CONSTRAINT ck_shop_price_currency
        CHECK (price_currency IN ('CNY', 'DIAMOND', 'ROOM_CARD', 'COUPON', 'FREE'));

WITH recorder_products(
    id,
    product_code,
    display_name,
    price_currency,
    price_amount,
    reward_quantity,
    sort_order
) AS (
    VALUES
        ('10000000-0000-0000-0000-000000000707'::uuid, 'PROP_RECORDER_2_HOURS', '记牌器2小时', 'ROOM_CARD', 3::bigint, 120::bigint, 707),
        ('10000000-0000-0000-0000-000000000708'::uuid, 'PROP_RECORDER_1_DAY', '记牌器1天', 'ROOM_CARD', 5::bigint, 1::bigint, 708),
        ('10000000-0000-0000-0000-000000000709'::uuid, 'PROP_RECORDER_3_DAYS', '记牌器3天', 'ROOM_CARD', 15::bigint, 3::bigint, 709),
        ('10000000-0000-0000-0000-000000000710'::uuid, 'PROP_RECORDER_7_DAYS', '记牌器7天', 'ROOM_CARD', 24::bigint, 7::bigint, 710),
        ('10000000-0000-0000-0000-000000000711'::uuid, 'PROP_RECORDER_1_ROUND', '记牌器1局', 'DIAMOND', 20::bigint, 1::bigint, 711),
        ('10000000-0000-0000-0000-000000000712'::uuid, 'PROP_RECORDER_10_ROUNDS', '记牌器10局', 'DIAMOND', 200::bigint, 10::bigint, 712),
        ('10000000-0000-0000-0000-000000000713'::uuid, 'PROP_RECORDER_20_ROUNDS', '记牌器20局', 'DIAMOND', 400::bigint, 20::bigint, 713)
)
INSERT INTO shop_products (
    id,
    product_code,
    category,
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
)
SELECT
    id,
    product_code,
    'prop',
    display_name,
    'recorder',
    price_currency,
    price_amount,
    'INVENTORY_PROP',
    reward_quantity,
    sort_order,
    TRUE,
    NULL,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM recorder_products;

INSERT INTO shop_product_rewards (
    product_id,
    reward_type,
    reward_quantity,
    item_code,
    grant_order,
    purchase_number
)
SELECT
    shop_products.id,
    'INVENTORY_PROP',
    reward.item_quantity,
    reward.item_code,
    1,
    0
FROM (
    VALUES
        ('PROP_RECORDER_2_HOURS', 'SHOP_RECORDER_MINUTE', 120::bigint),
        ('PROP_RECORDER_1_DAY', 'SHOP_RECORDER_DAY', 1::bigint),
        ('PROP_RECORDER_3_DAYS', 'SHOP_RECORDER_DAY', 3::bigint),
        ('PROP_RECORDER_7_DAYS', 'SHOP_RECORDER_DAY', 7::bigint),
        ('PROP_RECORDER_1_ROUND', 'SHOP_RECORDER_ROUND', 1::bigint),
        ('PROP_RECORDER_10_ROUNDS', 'SHOP_RECORDER_ROUND', 10::bigint),
        ('PROP_RECORDER_20_ROUNDS', 'SHOP_RECORDER_ROUND', 20::bigint)
) AS reward(product_code, item_code, item_quantity)
JOIN shop_products ON shop_products.product_code = reward.product_code;
