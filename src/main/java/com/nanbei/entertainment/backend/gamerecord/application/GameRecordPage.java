package com.nanbei.entertainment.backend.gamerecord.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GameRecordPage(
        LocalDate date,
        boolean membershipActive,
        List<Long> gameIds,
        Summary summary,
        List<Record> records) {
    public GameRecordPage {
        gameIds = List.copyOf(gameIds);
        records = List.copyOf(records);
    }

    public record Summary(int championCount, long score, int roundCount) {}

    public record Record(
            UUID sessionId,
            String roomNumber,
            long gameId,
            String gameName,
            boolean gold,
            int finishedRounds,
            int totalRounds,
            Instant finishedAt,
            List<Player> players) {
        public Record {
            players = List.copyOf(players);
        }
    }

    public record Player(
            long publicPlayerId,
            String displayName,
            long score,
            boolean host,
            boolean self) {}
}
