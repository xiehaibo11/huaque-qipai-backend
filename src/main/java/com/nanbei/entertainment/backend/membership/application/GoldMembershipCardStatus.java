package com.nanbei.entertainment.backend.membership.application;

public record GoldMembershipCardStatus(
        String productCode,
        String title,
        int durationDays,
        long dailyCoins,
        String state,
        long remainingSeconds) {}
