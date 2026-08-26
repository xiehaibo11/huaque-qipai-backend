package com.nanbei.entertainment.backend.scoreassistant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "score_ledger_players")
public class ScoreLedgerPlayerEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ledger_id", nullable = false)
    private ScoreLedgerEntity ledger;

    @Column(nullable = false)
    private short position;

    @Column(name = "display_name", nullable = false, length = 40)
    private String displayName;

    @Column(name = "owner_player", nullable = false)
    private boolean ownerPlayer;

    @Column(name = "total_score", nullable = false)
    private long totalScore;

    protected ScoreLedgerPlayerEntity() {}

    ScoreLedgerPlayerEntity(
            ScoreLedgerEntity ledger, int position, String displayName, boolean ownerPlayer) {
        id = UUID.randomUUID();
        this.ledger = ledger;
        this.position = (short) position;
        this.displayName = displayName;
        this.ownerPlayer = ownerPlayer;
    }

    public UUID getId() {
        return id;
    }

    public short getPosition() {
        return position;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isOwnerPlayer() {
        return ownerPlayer;
    }

    public long getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(long totalScore) {
        this.totalScore = totalScore;
    }
}
