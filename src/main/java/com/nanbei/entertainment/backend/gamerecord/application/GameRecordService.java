package com.nanbei.entertainment.backend.gamerecord.application;

import com.nanbei.entertainment.backend.gamerecord.infrastructure.GameRecordRepository;
import com.nanbei.entertainment.backend.membership.application.MembershipStatusService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameRecordService {
    private static final ZoneId ORIGINAL_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final MembershipStatusService membershipStatusService;
    private final GameRecordRepository repository;
    private final Clock clock;

    @Autowired
    public GameRecordService(
            MembershipStatusService membershipStatusService,
            GameRecordRepository repository) {
        this(membershipStatusService, repository, Clock.systemUTC());
    }

    public GameRecordService(
            MembershipStatusService membershipStatusService,
            GameRecordRepository repository,
            Clock clock) {
        this.membershipStatusService = membershipStatusService;
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public GameRecordPage page(
            UUID userId, LocalDate date, long gameId, GameRecordMode mode) {
        LocalDate selectedDate = date == null
                ? LocalDate.now(clock.withZone(ORIGINAL_TIME_ZONE))
                : date;
        long selectedGameId = Math.max(0L, gameId);
        boolean membershipActive = membershipStatusService.isActive(userId);
        if (mode == GameRecordMode.GOLD && !membershipActive) {
            return emptyPage(selectedDate, false);
        }

        List<GameRecordRepository.Row> rows = repository.find(
                userId, selectedDate, selectedGameId, mode == GameRecordMode.GOLD);
        List<GameRecordPage.Record> records = records(userId, mode, rows);
        return new GameRecordPage(
                selectedDate,
                membershipActive,
                gameIds(rows),
                summary(records),
                records);
    }

    private static GameRecordPage emptyPage(LocalDate date, boolean membershipActive) {
        return new GameRecordPage(
                date,
                membershipActive,
                List.of(),
                new GameRecordPage.Summary(0, 0L, 0),
                List.of());
    }

    private static List<Long> gameIds(List<GameRecordRepository.Row> rows) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        rows.forEach(row -> ids.add(row.gameId()));
        return List.copyOf(ids);
    }

    private static List<GameRecordPage.Record> records(
            UUID viewer, GameRecordMode mode, List<GameRecordRepository.Row> rows) {
        Map<UUID, List<GameRecordRepository.Row>> grouped = new LinkedHashMap<>();
        rows.forEach(row -> grouped.computeIfAbsent(row.sessionId(), ignored -> new ArrayList<>())
                .add(row));
        List<GameRecordPage.Record> records = new ArrayList<>();
        for (List<GameRecordRepository.Row> sessionRows : grouped.values()) {
            GameRecordRepository.Row first = sessionRows.getFirst();
            List<GameRecordPage.Player> players = sessionRows.stream()
                    .map(row -> new GameRecordPage.Player(
                            row.publicPlayerId(),
                            row.displayName(),
                            row.score(),
                            row.host(),
                            row.userId().equals(viewer)))
                    .toList();
            records.add(new GameRecordPage.Record(
                    first.sessionId(),
                    first.roomNumber(),
                    first.gameId(),
                    gameName(first.gameId()),
                    mode == GameRecordMode.GOLD,
                    first.finishedRounds(),
                    first.totalRounds(),
                    first.finishedAt(),
                    players));
        }
        return List.copyOf(records);
    }

    private static GameRecordPage.Summary summary(List<GameRecordPage.Record> records) {
        int champions = 0;
        long score = 0L;
        for (GameRecordPage.Record record : records) {
            GameRecordPage.Player self = record.players().stream()
                    .filter(GameRecordPage.Player::self)
                    .findFirst()
                    .orElse(null);
            if (self == null) {
                continue;
            }
            score += self.score();
            long highest = record.players().stream()
                    .mapToLong(GameRecordPage.Player::score)
                    .max()
                    .orElse(Long.MIN_VALUE);
            if (self.score() == highest) {
                champions++;
            }
        }
        return new GameRecordPage.Summary(champions, score, records.size());
    }

    private static String gameName(long gameId) {
        return switch ((int) gameId) {
            case 30109, 30400 -> "台州麻将";
            case 30284 -> "挖花玩法";
            case 30588 -> "茶苑双扣";
            default -> "游戏" + gameId;
        };
    }
}
