package com.nanbei.entertainment.backend.roomtools.domain;

import com.nanbei.entertainment.backend.roomtools.application.RoomToolType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "room_tool_reservations")
public class RoomToolReservationEntity {
    @Id private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tool_type", nullable = false, length = 32)
    private RoomToolType toolType;

    @Column(name = "target_round", nullable = false)
    private int targetRound;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RoomToolReservationEntity() {}

    public RoomToolReservationEntity(
            UUID sessionId,
            UUID userId,
            RoomToolType toolType,
            int targetRound,
            Instant occurredAt) {
        if (targetRound <= 0) {
            throw new IllegalArgumentException("targetRound must be positive");
        }
        id = UUID.randomUUID();
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.toolType = Objects.requireNonNull(toolType, "toolType");
        this.targetRound = targetRound;
        active = true;
        createdAt = Objects.requireNonNull(occurredAt, "occurredAt");
        updatedAt = occurredAt;
    }

    public void setActive(boolean value, Instant occurredAt) {
        active = value;
        updatedAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public RoomToolType getToolType() {
        return toolType;
    }

    public int getTargetRound() {
        return targetRound;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
