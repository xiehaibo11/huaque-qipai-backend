package com.nanbei.entertainment.backend.scoreassistant.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "score_ledger_rounds")
public class ScoreLedgerRoundEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ledger_id", nullable = false)
    private ScoreLedgerEntity ledger;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @OneToMany(mappedBy = "round", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScoreLedgerRoundScoreEntity> scores = new ArrayList<>();

    protected ScoreLedgerRoundEntity() {}

    public ScoreLedgerRoundEntity(
            ScoreLedgerEntity ledger,
            int roundNumber,
            Instant recordedAt,
            Map<ScoreLedgerPlayerEntity, Long> deltas,
            Map<ScoreLedgerPlayerEntity, Long> totals) {
        id = UUID.randomUUID();
        this.ledger = ledger;
        this.roundNumber = roundNumber;
        this.recordedAt = recordedAt;
        for (ScoreLedgerPlayerEntity player : ledger.getPlayers()) {
            scores.add(new ScoreLedgerRoundScoreEntity(
                    this, player, deltas.get(player), totals.get(player)));
        }
    }

    public UUID getId() {
        return id;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public List<ScoreLedgerRoundScoreEntity> getScores() {
        return Collections.unmodifiableList(scores);
    }
}
