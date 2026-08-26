package com.nanbei.entertainment.backend.scoreassistant.application;

import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerRoundEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ScoreRoundResponse(
        UUID roundId,
        int roundNumber,
        Instant recordedAt,
        List<Score> scores) {
    public ScoreRoundResponse {
        scores = List.copyOf(scores);
    }

    public static ScoreRoundResponse from(ScoreLedgerRoundEntity round) {
        return new ScoreRoundResponse(
                round.getId(),
                round.getRoundNumber(),
                round.getRecordedAt(),
                round.getScores().stream()
                        .sorted(java.util.Comparator.comparingInt(
                                score -> score.getPlayer().getPosition()))
                        .map(score -> new Score(
                                score.getPlayer().getId(),
                                score.getScoreDelta(),
                                score.getTotalAfter()))
                        .toList());
    }

    public record Score(UUID playerId, long scoreDelta, long totalAfter) {}
}
