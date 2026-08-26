WITH vehicle_products(
    id,
    product_code,
    display_name,
    icon_key,
    price_amount,
    item_code,
    sort_order
) AS (
    VALUES
        ('10000000-0000-0000-0000-000000001501'::uuid, 'DECORATION_VEHICLE_150801', '二八大杠7天', 'vehicle_150801', 300::bigint, 'PROP_RQDH_150801', 890),
        ('10000000-0000-0000-0000-000000001502'::uuid, 'DECORATION_VEHICLE_150802', '北欧幽灵7天', 'vehicle_150802', 1500::bigint, 'PROP_RQDH_150802', 891),
        ('10000000-0000-0000-0000-000000001503'::uuid, 'DECORATION_VEHICLE_150804', '暗夜精灵7天', 'vehicle_150804', 1500::bigint, 'PROP_RQDH_150804', 892),
        ('10000000-0000-0000-0000-000000001504'::uuid, 'DECORATION_VEHICLE_150803', '冰蓝狂啸7天', 'vehicle_150803', 1500::bigint, 'PROP_RQDH_150803', 893),
        ('10000000-0000-0000-0000-000000001505'::uuid, 'DECORATION_VEHICLE_150808', '红色疾风7天', 'vehicle_150808', 1500::bigint, 'PROP_RQDH_150808', 894),
        ('10000000-0000-0000-0000-000000001506'::uuid, 'DECORATION_VEHICLE_150807', '极速幻影7天', 'vehicle_150807', 1500::bigint, 'PROP_RQDH_150807', 895),
        ('10000000-0000-0000-0000-000000001507'::uuid, 'DECORATION_VEHICLE_150806', '跃马风情7天', 'vehicle_150806', 1500::bigint, 'PROP_RQDH_150806', 896),
        ('10000000-0000-0000-0000-000000001508'::uuid, 'DECORATION_VEHICLE_150805', '英伦领航者7天', 'vehicle_150805', 1500::bigint, 'PROP_RQDH_150805', 897),
        ('10000000-0000-0000-0000-000000001509'::uuid, 'DECORATION_VEHICLE_150816', '越野家7天', 'vehicle_150816', 1500::bigint, 'PROP_RQDH_150816', 898)
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
    'decoration',
    'enterani',
    display_name,
    icon_key,
    'DIAMOND',
    price_amount,
    'DECORATION_PROP',
    7,
    sort_order,
    TRUE,
    NULL,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM vehicle_products;

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
    'DECORATION_PROP',
    7,
    reward.item_code,
    1,
    0
FROM (
    VALUES
        ('DECORATION_VEHICLE_150801', 'PROP_RQDH_150801'),
        ('DECORATION_VEHICLE_150802', 'PROP_RQDH_150802'),
        ('DECORATION_VEHICLE_150804', 'PROP_RQDH_150804'),
        ('DECORATION_VEHICLE_150803', 'PROP_RQDH_150803'),
        ('DECORATION_VEHICLE_150808', 'PROP_RQDH_150808'),
        ('DECORATION_VEHICLE_150807', 'PROP_RQDH_150807'),
        ('DECORATION_VEHICLE_150806', 'PROP_RQDH_150806'),
        ('DECORATION_VEHICLE_150805', 'PROP_RQDH_150805'),
        ('DECORATION_VEHICLE_150816', 'PROP_RQDH_150816')
) AS reward(product_code, item_code)
JOIN shop_products ON shop_products.product_code = reward.product_code;
