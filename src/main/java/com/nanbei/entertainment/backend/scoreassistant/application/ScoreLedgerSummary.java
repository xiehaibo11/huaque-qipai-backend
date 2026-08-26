package com.nanbei.entertainment.backend.scoreassistant.application;

import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerEntity;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ScoreLedgerSummary(
        UUID ledgerId,
        ScoreLedgerStatus status,
        boolean favorite,
        int roundCount,
        Instant startedAt,
        Instant endedAt,
        List<Player> players) {
    public ScoreLedgerSummary {
        players = List.copyOf(players);
    }

    public static ScoreLedgerSummary from(ScoreLedgerEntity ledger) {
        return new ScoreLedgerSummary(
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
                        .toList());
    }

    public record Player(
            UUID playerId,
            int position,
            String name,
            boolean ownerPlayer,
            long totalScore) {}
}
