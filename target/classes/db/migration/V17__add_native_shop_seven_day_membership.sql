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
    '10000000-0000-0000-0000-000000000105'::uuid,
    'SXVIP_7_DAYS',
    'time_membership',
    '7天会员',
    'vip_gift',
    'CNY',
    2500,
    'MEMBERSHIP_DAY',
    7,
    105,
    TRUE,
    NULL,
    NULL,
    payment_products.id,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM payment_products
WHERE payment_products.product_code = 'SXVIP_7_DAYS';

INSERT INTO shop_product_rewards (
    product_id,
    reward_type,
    reward_quantity,
    item_code,
    grant_order,
    purchase_number
)
VALUES (
    '10000000-0000-0000-0000-000000000105'::uuid,
    'MEMBERSHIP_DAY',
    7,
    NULL,
    1,
    0
);
