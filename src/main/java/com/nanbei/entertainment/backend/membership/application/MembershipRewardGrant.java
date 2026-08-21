package com.nanbei.entertainment.backend.membership.application;

public record MembershipRewardGrant(
        String code,
        String displayName,
        long quantity,
        Integer durationDays,
        String iconKey) {}
