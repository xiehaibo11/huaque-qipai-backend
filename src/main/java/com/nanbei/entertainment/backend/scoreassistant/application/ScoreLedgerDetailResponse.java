package com.nanbei.entertainment.backend.scoreassistant.application;

import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerEntity;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerRoundEntity;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ScoreLedgerDetailResponse(
        UUID ledgerId,
        ScoreLedgerStatus status,
        boolean favorite,
        int roundCount,
        Instant startedAt,
        Instant endedAt,
        List<Player> players,
        List<Round> rounds) {
    public ScoreLedgerDetailResponse {
        players = List.copyOf(players);
        rounds = List.copyOf(rounds);
    }

    public static ScoreLedgerDetailResponse created(ScoreLedgerEntity ledger) {
        return from(ledger, List.of());
    }

    public static ScoreLedgerDetailResponse from(
            ScoreLedgerEntity ledger, List<ScoreLedgerRoundEntity> rounds) {
        return new ScoreLedgerDetailResponse(
                ledger.getId(),
                ledger.getStatus(),
                ledger.isFavorite(),
                ledger.getRoundCount(),
                ledger.getStartedAt(),
                ledger.getEndedAt(),
                ledger.getPlayers().stream()
                        .sorted(java.util.Comparator.comparingInt(
                                player -> player.getPosition()))
                        .map(player -> new Player(
                                player.getId(),
                                player.getPosition(),
                                player.getDisplayName(),
                                player.isOwnerPlayer(),
                                player.getTotalScore()))
                        .toList(),
                rounds.stream()
                        .map(round -> new Round(
                                round.getId(),
                                round.getRoundNumber(),
                                round.getRecordedAt(),
                                round.getScores().stream()
                                        .sorted(java.util.Comparator.comparingInt(
                                                score -> score.getPlayer().getPosition()))
                                        .map(score -> new Score(
                                                score.getPlayer().getId(),
                                                score.getPlayer().getDisplayName(),
                                                score.getScoreDelta(),
                                                score.getTotalAfter()))
                                        .toList()))
                        .toList());
    }

    public record Player(
            UUID playerId,
            int position,
            String name,
            boolean ownerPlayer,
            long totalScore) {}

    public record Round(
            UUID roundId,
            int roundNumber,
            Instant recordedAt,
            List<Score> scores) {
        public Round {
            scores = List.copyOf(scores);
        }
    }

    public record Score(
            UUID playerId, String playerName, long scoreDelta, long totalAfter) {}
}
