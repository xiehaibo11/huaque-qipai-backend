package com.nanbei.entertainment.backend.membership.application;

import java.util.List;
import java.util.UUID;

public record MembershipProductResponse(
        UUID productId,
        String productCode,
        String planCode,
        String name,
        long amountMinor,
        String currency,
        int durationDays,
        int giftValueYuan,
        String priceText,
        String dayCostText,
        String cardStyle,
        String cornerTag,
        boolean subscription,
        int privilegesCount,
        int dailyGiftValueYuan,
        int sortOrder,
        List<MembershipProductReward> rewards) {}
