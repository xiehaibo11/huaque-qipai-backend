package com.nanbei.entertainment.backend.membership.application;

public record MembershipDailyGiftReward(
        String code,
        String displayName,
        long quantity,
        String subtitle,
        String iconKey,
        Integer durationDays) {}
