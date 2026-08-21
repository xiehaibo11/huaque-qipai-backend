package com.nanbei.entertainment.backend.membership.application;

import java.time.Instant;
import java.util.List;

public record MembershipNoticeResponse(
        int version,
        String title,
        List<String> items,
        String changeNotice,
        String agreementTitle,
        String agreementUrl,
        Instant updatedAt) {
    public MembershipNoticeResponse {
        items = List.copyOf(items);
    }
}
