package com.nanbei.entertainment.backend.fortune.application;

import com.nanbei.entertainment.backend.shop.application.ShopWalletResponse;

public record FortunePrayerResponse(
        String productCode,
        int quantity,
        long spentDiamonds,
        int wealthPoints,
        int luckPoints,
        ShopWalletResponse wallet,
        boolean replayed) {
    FortunePrayerResponse asReplay() {
        return new FortunePrayerResponse(
                productCode,
                quantity,
                spentDiamonds,
                wealthPoints,
                luckPoints,
                wallet,
                true);
    }
}
