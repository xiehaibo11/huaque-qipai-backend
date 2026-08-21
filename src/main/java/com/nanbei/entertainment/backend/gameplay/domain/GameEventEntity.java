package com.nanbei.entertainment.backend.gameplay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "game_events")
public class GameEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(nullable = false)
    private long revision;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "event_order", nullable = false)
    private int eventOrder;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 16)
    private GameEvent.Audience visibility;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "target_seat")
    private Integer targetSeat;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GameEventEntity() {}

    private GameEventEntity(
            UUID sessionId,
            long revision,
            int eventOrder,
            String eventType,
            GameEvent.Audience visibility,
            Integer targetSeat,
            String payload,
            Instant occurredAt) {
        if (revision <= 0) {
            throw new IllegalArgumentException("revision must be positive");
        }
        if (eventOrder <= 0) {
            throw new IllegalArgumentException("eventOrder must be positive");
        }
        if (visibility == GameEvent.Audience.PUBLIC && targetSeat != null) {
            throw new IllegalArgumentException("public event cannot target a seat");
        }
        if (visibility == GameEvent.Audience.SEAT && (targetSeat == null || targetSeat <= 0)) {
            throw new IllegalArgumentException("private event requires a positive target seat");
        }
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.revision = revision;
        this.eventOrder = eventOrder;
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.targetSeat = targetSeat;
        this.payload = Objects.requireNonNull(payload, "payload");
        this.createdAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static GameEventEntity publicEvent(
            UUID sessionId,
            long revision,
            int eventOrder,
            String eventType,
            String payload,
            Instant occurredAt) {
        return new GameEventEntity(
                sessionId,
                revision,
                eventOrder,
                eventType,
                GameEvent.Audience.PUBLIC,
                null,
                payload,
                occurredAt);
    }

    public static GameEventEntity seatEvent(
            UUID sessionId,
            long revision,
            int eventOrder,
            String eventType,
            int targetSeat,
            String payload,
            Instant occurredAt) {
        return new GameEventEntity(
                sessionId,
                revision,
                eventOrder,
                eventType,
                GameEvent.Audience.SEAT,
                targetSeat,
                payload,
                occurredAt);
    }

    public long getRevision() {
        return revision;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public int getEventOrder() {
        return eventOrder;
    }

    public String getEventType() {
        return eventType;
    }

    public GameEvent.Audience getVisibility() {
        return visibility;
    }

    public Integer getTargetSeat() {
        return targetSeat;
    }

    public String getPayload() {
        return payload;
    }
}
