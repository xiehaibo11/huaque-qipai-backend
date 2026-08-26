package com.nanbei.entertainment.backend.scoreassistant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "score_ledger_round_scores")
public class ScoreLedgerRoundScoreEntity {
    @EmbeddedId
    private ScoreLedgerRoundScoreId id;

    @MapsId("roundId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    private ScoreLedgerRoundEntity round;

    @MapsId("playerId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private ScoreLedgerPlayerEntity player;

    @Column(name = "score_delta", nullable = false)
    private long scoreDelta;

    @Column(name = "total_after", nullable = false)
    private long totalAfter;

    protected ScoreLedgerRoundScoreEntity() {}

    ScoreLedgerRoundScoreEntity(
            ScoreLedgerRoundEntity round,
            ScoreLedgerPlayerEntity player,
            long scoreDelta,
            long totalAfter) {
        id = new ScoreLedgerRoundScoreId(round.getId(), player.getId());
        this.round = round;
        this.player = player;
        this.scoreDelta = scoreDelta;
        this.totalAfter = totalAfter;
    }

    public ScoreLedgerPlayerEntity getPlayer() {
        return player;
    }

    public long getScoreDelta() {
        return scoreDelta;
    }

    public long getTotalAfter() {
        return totalAfter;
    }
}
