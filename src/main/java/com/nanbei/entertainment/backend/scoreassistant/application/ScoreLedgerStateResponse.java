package com.nanbei.entertainment.backend.scoreassistant.application;

import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerEntity;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerStatus;
import java.time.Instant;
import java.util.UUID;

public record ScoreLedgerStateResponse(
        UUID ledgerId,
        ScoreLedgerStatus status,
        boolean favorite,
        int roundCount,
        Instant endedAt) {
    public static ScoreLedgerStateResponse from(ScoreLedgerEntity ledger) {
        return new ScoreLedgerStateResponse(
                ledger.getId(),
                ledger.getStatus(),
                ledger.isFavorite(),
                ledger.getRoundCount(),
                ledger.getEndedAt());
    }
}
