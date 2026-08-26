package com.nanbei.entertainment.backend.scoreassistant.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "score_ledgers")
public class ScoreLedgerEntity {
    @Id
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScoreLedgerStatus status;

    @Column(nullable = false)
    private boolean favorite;

    @Column(name = "round_count", nullable = false)
    private int roundCount;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "ledger", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScoreLedgerPlayerEntity> players = new ArrayList<>();

    protected ScoreLedgerEntity() {}

    public ScoreLedgerEntity(
            UUID ownerUserId, List<NamedPlayer> namedPlayers, Instant now) {
        id = UUID.randomUUID();
        this.ownerUserId = ownerUserId;
        status = ScoreLedgerStatus.IN_PROGRESS;
        startedAt = now;
        createdAt = now;
        updatedAt = now;
        for (int index = 0; index < namedPlayers.size(); index++) {
            NamedPlayer player = namedPlayers.get(index);
            players.add(new ScoreLedgerPlayerEntity(
                    this, index + 1, player.name(), player.ownerPlayer()));
        }
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public ScoreLedgerStatus getStatus() {
        return status;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public int getRoundCount() {
        return roundCount;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public List<ScoreLedgerPlayerEntity> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    public void incrementRoundCount() {
        roundCount++;
    }

    public void end(Instant now) {
        status = ScoreLedgerStatus.ENDED;
        endedAt = now;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public void delete(Instant now) {
        deletedAt = now;
    }

    public record NamedPlayer(String name, boolean ownerPlayer) {}
}
