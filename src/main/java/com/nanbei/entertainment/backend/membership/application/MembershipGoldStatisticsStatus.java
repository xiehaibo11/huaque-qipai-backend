package com.nanbei.entertainment.backend.membership.application;

import java.time.LocalDate;
import java.util.List;

public record MembershipGoldStatisticsStatus(
        boolean membershipActive,
        long selectedGameId,
        LocalDate startDate,
        LocalDate endDate,
        List<Long> gameId,
        Period today,
        Period yesterday,
        Period lastThree,
        Period lastSeven) {
    public MembershipGoldStatisticsStatus {
        gameId = List.copyOf(gameId);
    }

    public record Period(
            String code,
            String label,
            long fightCnt,
            int winRate,
            long winScore) {}
}
