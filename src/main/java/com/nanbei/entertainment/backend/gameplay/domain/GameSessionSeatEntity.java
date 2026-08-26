package com.nanbei.entertainment.backend.gameplay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "game_session_seats")
public class GameSessionSeatEntity {
    @EmbeddedId private GameSessionSeatId id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private boolean ready;

    @Column(nullable = false)
    private boolean connected;

    @Column(nullable = false)
    private long score = 1000L;

    @Column(name = "last_ack_revision", nullable = false)
    private long lastAckRevision;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GameSessionSeatEntity() {}

    public GameSessionSeatEntity(
            UUID sessionId, int seatNumber, UUID userId, Instant occurredAt) {
        this(sessionId, seatNumber, userId, 1000L, occurredAt);
    }

    public GameSessionSeatEntity(
            UUID sessionId,
            int seatNumber,
            UUID userId,
            long initialScore,
            Instant occurredAt) {
        if (initialScore < 0) {
            throw new IllegalArgumentException("initialScore must not be negative");
        }
        this.id = new GameSessionSeatId(sessionId, seatNumber);
        this.userId = Objects.requireNonNull(userId, "userId");
        this.score = initialScore;
        this.createdAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.updatedAt = occurredAt;
    }

    public void setReady(boolean nextReady, Instant occurredAt) {
        ready = nextReady;
        updatedAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public void setConnected(boolean nextConnected, Instant occurredAt) {
        connected = nextConnected;
        updatedAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }


    public void applyScoreDelta(long delta, Instant occurredAt) {
        score = Math.addExact(score, delta);
        updatedAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public void replaceOccupant(UUID nextUserId, long initialScore, Instant occurredAt) {
        if (initialScore < 0) {
            throw new IllegalArgumentException("initialScore must not be negative");
        }
        userId = Objects.requireNonNull(nextUserId, "nextUserId");
        score = initialScore;
        ready = true;
        connected = true;
        lastAckRevision = 0L;
        updatedAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public void acknowledge(long acknowledgedRevision, Instant occurredAt) {
        if (acknowledgedRevision < 0) {
            throw new IllegalArgumentException("acknowledgedRevision must not be negative");
        }
        if (acknowledgedRevision > lastAckRevision) {
            lastAckRevision = acknowledgedRevision;
            updatedAt = Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    public GameSessionSeatId getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isConnected() {
        return connected;
    }

    public long getScore() {
        return score;
    }

    public long getLastAckRevision() {
        return lastAckRevision;
    }
}
