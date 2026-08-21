package com.nanbei.entertainment.backend.gameplay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "game_sessions")
public class GameSessionEntity {
    @Id private UUID id;

    @Column(name = "room_id", nullable = false, unique = true)
    private UUID roomId;

    @Column(name = "game_id", nullable = false)
    private long gameId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GamePhase phase;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @Column(nullable = false)
    private long revision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String state;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GameSessionEntity() {}

    public GameSessionEntity(UUID roomId, long gameId, Instant occurredAt) {
        this(UUID.randomUUID(), roomId, gameId, occurredAt);
    }

    GameSessionEntity(UUID id, UUID roomId, long gameId, Instant occurredAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.roomId = Objects.requireNonNull(roomId, "roomId");
        if (gameId <= 0) {
            throw new IllegalArgumentException("gameId must be positive");
        }
        this.gameId = gameId;
        this.phase = GamePhase.WAITING;
        this.state = "{}";
        this.createdAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.updatedAt = occurredAt;
    }

    public void advance(
            GamePhase nextPhase,
            int nextRoundNumber,
            long nextRevision,
            String nextState,
            Instant occurredAt) {
        if (nextRevision != revision + 1) {
            throw new IllegalArgumentException("next revision must advance exactly once");
        }
        if (nextRoundNumber < roundNumber) {
            throw new IllegalArgumentException("round number cannot move backwards");
        }
        phase = Objects.requireNonNull(nextPhase, "nextPhase");
        roundNumber = nextRoundNumber;
        revision = nextRevision;
        state = Objects.requireNonNull(nextState, "nextState");
        updatedAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /**
     * 局数用尽后直接完结会话（南北自建 QA 多局流转）：不改 revision、不写事件，
     * 由命令事务在不回滚的拒绝路径上落库。
     */
    public void complete(Instant occurredAt) {
        phase = GamePhase.COMPLETED;
        updatedAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public long getGameId() {
        return gameId;
    }

    public GamePhase getPhase() {
        return phase;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public long getRevision() {
        return revision;
    }

    public String getState() {
        return state;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
