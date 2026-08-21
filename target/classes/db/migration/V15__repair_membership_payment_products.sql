INSERT INTO payment_products (
    id,
    product_code,
    name,
    amount_minor,
    currency,
    enabled,
    created_at,
    updated_at
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
    enabled = TRUE,
    updated_at = CURRENT_TIMESTAMP;
