package com.nanbei.entertainment.backend.fortune.application;

import com.nanbei.entertainment.backend.shop.application.ShopWalletResponse;
import java.util.List;

public record FortuneTreasureDrawResponse(
        int count,
        long spentDiamonds,
        List<FortuneTreasureDrawItem> draws,
        ShopWalletResponse wallet,
        boolean replayed) {
    FortuneTreasureDrawResponse asReplay() {
        return new FortuneTreasureDrawResponse(count, spentDiamonds, draws, wallet, true);
    }
}
