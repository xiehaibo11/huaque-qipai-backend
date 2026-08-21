package com.nanbei.entertainment.backend.membership.application;

public record MembershipProductReward(
        String code,
        String displayName,
        long quantity,
        String countText,
        Integer durationDays,
        String iconKey) {}
