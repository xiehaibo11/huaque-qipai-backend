package com.nanbei.entertainment.backend.shop.application;

import com.nanbei.entertainment.backend.shop.domain.ShopProductEntity;

public record ShopProductResponse(
        String productCode,
        String category,
        String displayName,
        String iconKey,
        String priceCurrency,
        long priceAmount,
        String rewardType,
        long rewardQuantity,
        int sortOrder,
        Integer dailyLimit,
        Integer lifetimeLimit,
        boolean enabled) {
    static ShopProductResponse from(ShopProductEntity product) {
        return new ShopProductResponse(
                product.getProductCode(),
                product.getCategory(),
                product.getDisplayName(),
                product.getIconKey(),
                product.getPriceCurrency(),
                product.getPriceAmount(),
                product.getRewardType(),
                product.getRewardQuantity(),
                product.getSortOrder(),
                product.getDailyLimit(),
                product.getLifetimeLimit(),
                product.isEnabled());
    }
}
