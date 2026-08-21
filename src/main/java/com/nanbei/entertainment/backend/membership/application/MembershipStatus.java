package com.nanbei.entertainment.backend.membership.application;

import java.time.Instant;

public record MembershipStatus(
        boolean membershipActive,
        int membershipLevel,
        Instant startedAt,
        Instant expiresAt,
        boolean autoRenew,
        long remainingDays) {}
