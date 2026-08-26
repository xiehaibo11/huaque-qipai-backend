package com.nanbei.entertainment.backend.shop.application;

import com.nanbei.entertainment.backend.shop.domain.ShopProductEntity;

public record ShopProductResponse(
        String productCode,
        String category,
        String section,
        String displayName,
        String iconKey,
        String priceCurrency,
        long priceAmount,
        String rewardType,
        long rewardQuantity,
        int sortOrder,
        Integer dailyLimit,
        Integer lifetimeLimit,
        long purchasedToday,
        long purchasedLifetime,
        Integer remainingPurchases,
        boolean enabled) {
    static ShopProductResponse from(
            ShopProductEntity product, long purchasedToday, long purchasedLifetime) {
        return new ShopProductResponse(
                product.getProductCode(),
                product.getCategory(),
                product.getSection(),
                product.getDisplayName(),
                product.getIconKey(),
                product.getPriceCurrency(),
                product.getPriceAmount(),
                product.getRewardType(),
                product.getRewardQuantity(),
                product.getSortOrder(),
                product.getDailyLimit(),
                product.getLifetimeLimit(),
                purchasedToday,
                purchasedLifetime,
                remaining(product, purchasedToday, purchasedLifetime),
                product.isEnabled());
    }

    private static Integer remaining(
            ShopProductEntity product, long purchasedToday, long purchasedLifetime) {
        Integer remaining = null;
        if (product.getDailyLimit() != null) {
            remaining = available(product.getDailyLimit(), purchasedToday);
        }
        if (product.getLifetimeLimit() != null) {
            int lifetimeRemaining = available(product.getLifetimeLimit(), purchasedLifetime);
            remaining =
                    remaining == null
                            ? lifetimeRemaining
                            : Math.min(remaining, lifetimeRemaining);
        }
        return remaining;
    }

    private static int available(int limit, long purchased) {
        return (int) Math.max(0L, limit - purchased);
    }
}
