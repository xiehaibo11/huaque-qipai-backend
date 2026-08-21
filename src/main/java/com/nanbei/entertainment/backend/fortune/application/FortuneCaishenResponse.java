package com.nanbei.entertainment.backend.fortune.application;

import com.nanbei.entertainment.backend.shop.application.ShopWalletResponse;
import java.time.Instant;

public record FortuneCaishenResponse(
        String productCode,
        long spentDiamonds,
        Instant expiresAt,
        long remainingSeconds,
        ShopWalletResponse wallet,
        boolean replayed) {
    FortuneCaishenResponse asReplay() {
        return new FortuneCaishenResponse(
                productCode,
                spentDiamonds,
                expiresAt,
                remainingSeconds,
                wallet,
                true);
    }
}
