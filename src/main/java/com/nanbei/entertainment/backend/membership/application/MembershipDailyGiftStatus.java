package com.nanbei.entertainment.backend.membership.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MembershipDailyGiftStatus(
        boolean membershipActive,
        boolean claimedToday,
        Integer claimedGiftId,
        LocalDate serverDate,
        Instant claimedAt,
        List<MembershipDailyGiftOption> options,
        WalletSnapshot wallet) {
    public record WalletSnapshot(long roomCards, long boundRoomCards, long coins, long diamonds) {}
}
