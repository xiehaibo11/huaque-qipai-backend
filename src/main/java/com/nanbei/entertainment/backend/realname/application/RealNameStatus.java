package com.nanbei.entertainment.backend.realname.application;

import java.time.Instant;

public record RealNameStatus(
        String status,
        String realNameMasked,
        String idCardMasked,
        Instant verifiedAt,
        boolean alipayOneTapEnabled) {
    public static RealNameStatus unverified(boolean alipayOneTapEnabled) {
        return new RealNameStatus(
                "UNVERIFIED", null, null, null, alipayOneTapEnabled);
    }
}
