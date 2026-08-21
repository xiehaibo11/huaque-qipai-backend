package com.nanbei.entertainment.backend.fortune.application;

import com.nanbei.entertainment.backend.shop.application.ShopWalletResponse;
import java.time.Instant;
import java.util.List;

public record FortuneStateResponse(
        ShopWalletResponse wallet,
        int wealthPoints,
        int luckPoints,
        List<FortunePrayerProduct> prayerProducts,
        List<FortuneTreasureProduct> treasureProducts,
        List<FortuneCaishenProduct> caishenProducts,
        List<FortuneTreasureView> treasures,
        Instant caishenExpiresAt,
        long caishenRemainingSeconds,
        long treasureOneDrawPriceDiamonds,
        long treasureFiveDrawPriceDiamonds,
        int treasureFiveDrawDiscountTenths) {}
