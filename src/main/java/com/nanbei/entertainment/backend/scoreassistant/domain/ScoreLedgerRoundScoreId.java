package com.nanbei.entertainment.backend.scoreassistant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ScoreLedgerRoundScoreId implements Serializable {
    @Column(name = "round_id")
    private UUID roundId;

    @Column(name = "player_id")
    private UUID playerId;

    protected ScoreLedgerRoundScoreId() {}

    ScoreLedgerRoundScoreId(UUID roundId, UUID playerId) {
        this.roundId = roundId;
        this.playerId = playerId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ScoreLedgerRoundScoreId that)) {
            return false;
        }
        return Objects.equals(roundId, that.roundId)
                && Objects.equals(playerId, that.playerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roundId, playerId);
    }
}
