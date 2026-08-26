WITH decoration_products(product_code, section, display_name) AS (
    VALUES
        ('DECORATION_TABLE_1', 'tablebg', '财神桌布7天'),
        ('DECORATION_TABLE_2', 'tablebg', '招财桌布7天'),
        ('DECORATION_TABLE_3', 'pb', '运旺气旺牌背7天'),
        ('DECORATION_TABLE_4', 'tablebg', '牛气桌布7天'),
        ('DECORATION_TABLE_5', 'pb', '福气牌背7天'),
        ('DECORATION_TABLE_6', 'txk', '白银相框7天'),
        ('DECORATION_TABLE_7', 'tablebg', '鼠你最豪7天'),
        ('DECORATION_TABLE_8', 'txk', '浪漫花语7天'),
        ('DECORATION_TABLE_9', 'ypq', '麒麟祥瑞压牌器7天')
)
UPDATE shop_products
SET section = decoration_products.section,
    display_name = decoration_products.display_name,
    updated_at = CURRENT_TIMESTAMP
FROM decoration_products
WHERE shop_products.product_code = decoration_products.product_code;
