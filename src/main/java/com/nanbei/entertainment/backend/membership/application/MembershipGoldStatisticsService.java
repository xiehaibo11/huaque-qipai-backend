package com.nanbei.entertainment.backend.membership.application;

import com.nanbei.entertainment.backend.membership.infrastructure.MembershipGoldStatisticsRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipGoldStatisticsService {
    private static final ZoneId ORIGINAL_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final MembershipStatusService membershipStatusService;
    private final MembershipGoldStatisticsRepository statisticsRepository;
    private final Clock clock;

    @Autowired
    public MembershipGoldStatisticsService(
            MembershipStatusService membershipStatusService,
            MembershipGoldStatisticsRepository statisticsRepository) {
        this(membershipStatusService, statisticsRepository, Clock.systemUTC());
    }

    MembershipGoldStatisticsService(
            MembershipStatusService membershipStatusService,
            MembershipGoldStatisticsRepository statisticsRepository,
            Clock clock) {
        this.membershipStatusService = membershipStatusService;
        this.statisticsRepository = statisticsRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MembershipGoldStatisticsStatus status(UUID userId, long gameId) {
        long selectedGameId = Math.max(0L, gameId);
        LocalDate endDate = LocalDate.now(clock.withZone(ORIGINAL_TIME_ZONE));
        LocalDate startDate = endDate.minusDays(7);
        boolean membershipActive = membershipStatusService.isActive(userId);
        List<Long> gameIds = membershipActive
                ? statisticsRepository.findGameIds(userId, startDate, endDate)
                : List.of();
        return new MembershipGoldStatisticsStatus(
                membershipActive,
                selectedGameId,
                startDate,
                endDate,
                gameIds,
                period(userId, selectedGameId, "today", "今日", endDate, endDate, membershipActive),
                period(userId, selectedGameId, "yesterday", "昨日",
                        endDate.minusDays(1), endDate.minusDays(1), membershipActive),
                period(userId, selectedGameId, "lastThree", "最近3日",
                        endDate.minusDays(2), endDate, membershipActive),
                period(userId, selectedGameId, "lastSeven", "最近7日",
                        endDate.minusDays(6), endDate, membershipActive));
    }

    private MembershipGoldStatisticsStatus.Period period(
            UUID userId,
            long gameId,
            String code,
            String label,
            LocalDate startDate,
            LocalDate endDate,
            boolean membershipActive) {
        if (!membershipActive) {
            return new MembershipGoldStatisticsStatus.Period(code, label, 0L, 0, 0L);
        }
        MembershipGoldStatisticsRepository.Aggregate aggregate =
                statisticsRepository.aggregate(userId, gameId, startDate, endDate);
        return new MembershipGoldStatisticsStatus.Period(
                code,
                label,
                aggregate.fightCnt(),
                winRate(aggregate.fightCnt(), aggregate.winCnt()),
                aggregate.winScore());
    }

    private static int winRate(long fightCnt, long winCnt) {
        if (fightCnt <= 0 || winCnt <= 0) {
            return 0;
        }
        return (int) Math.round(winCnt * 100.0 / fightCnt);
    }
}
