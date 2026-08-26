package com.nanbei.entertainment.backend.freedraw.application;

import com.nanbei.entertainment.backend.shop.application.ShopWalletResponse;
import java.time.Instant;
import java.util.List;

public final class FreeDrawResponses {
    private FreeDrawResponses() {}

    public record PrizeView(
            String prizeId, String type, long amount, String displayName, String iconKey) {}

    public record StateResponse(
            String activityCode,
            String adPlacementId,
            int dailyLimit,
            int completedDraws,
            int remainingDraws,
            List<PrizeView> prizes,
            Instant serverTime) {}

    public record SessionResponse(
            String sessionId, String userCustomData, String adPlacementId, Instant expiresAt) {}

    public record RewardResponse(
            String sessionId,
            boolean replayed,
            PrizeView reward,
            int remainingDraws,
            ShopWalletResponse wallet) {}
}
