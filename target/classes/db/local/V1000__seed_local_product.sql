INSERT INTO payment_products (
    id, product_code, name, amount_minor, currency, enabled, created_at, updated_at
) VALUES (
    '00000000-0000-0000-0000-000000000100',
    'LOCAL_COIN_PACK_1',
    '本地测试金币包',
    100,
    'CNY',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (product_code) DO NOTHING;
