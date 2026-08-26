WITH prop_products(
    id,
    product_code,
    section,
    display_name,
    icon_key,
    price_amount,
    reward_quantity,
    item_code,
    sort_order
) AS (
    VALUES
        ('10000000-0000-0000-0000-000000000714'::uuid, 'PROP_WASH_CARD_1', 'wash_card', '洗牌券1张', 'wash_card', 20::bigint, 1::bigint, 'PROP_WASH_CARD', 714),
        ('10000000-0000-0000-0000-000000000715'::uuid, 'PROP_WASH_CARD_5', 'wash_card', '洗牌券5张', 'wash_card', 90::bigint, 5::bigint, 'PROP_WASH_CARD', 715),
        ('10000000-0000-0000-0000-000000000716'::uuid, 'PROP_WASH_CARD_10', 'wash_card', '洗牌券10张', 'wash_card', 160::bigint, 10::bigint, 'PROP_WASH_CARD', 716),
        ('10000000-0000-0000-0000-000000000717'::uuid, 'PROP_LUCK_BEAD_1', 'luck_prop', '转运珠1颗', 'luck_bead', 20::bigint, 1::bigint, 'PROP_LUCK_BEAD', 717),
        ('10000000-0000-0000-0000-000000000718'::uuid, 'PROP_LUCK_BEAD_5', 'luck_prop', '转运珠5颗', 'luck_bead', 90::bigint, 5::bigint, 'PROP_LUCK_BEAD', 718),
        ('10000000-0000-0000-0000-000000000719'::uuid, 'PROP_LUCK_BEAD_10', 'luck_prop', '转运珠10颗', 'luck_bead', 160::bigint, 10::bigint, 'PROP_LUCK_BEAD', 719)
)
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
)
SELECT
    id,
    product_code,
    'prop',
    section,
    display_name,
    icon_key,
    'DIAMOND',
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
FROM prop_products;

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
        ('PROP_WASH_CARD_1', 'PROP_WASH_CARD', 1::bigint),
        ('PROP_WASH_CARD_5', 'PROP_WASH_CARD', 5::bigint),
        ('PROP_WASH_CARD_10', 'PROP_WASH_CARD', 10::bigint),
        ('PROP_LUCK_BEAD_1', 'PROP_LUCK_BEAD', 1::bigint),
        ('PROP_LUCK_BEAD_5', 'PROP_LUCK_BEAD', 5::bigint),
        ('PROP_LUCK_BEAD_10', 'PROP_LUCK_BEAD', 10::bigint)
) AS reward(product_code, item_code, item_quantity)
JOIN shop_products ON shop_products.product_code = reward.product_code;
