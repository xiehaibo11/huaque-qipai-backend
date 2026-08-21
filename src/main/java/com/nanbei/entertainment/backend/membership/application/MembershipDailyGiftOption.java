package com.nanbei.entertainment.backend.membership.application;

import java.util.List;

public record MembershipDailyGiftOption(
        int giftId,
        String title,
        String buttonStyle,
        List<MembershipDailyGiftReward> rewards) {}
