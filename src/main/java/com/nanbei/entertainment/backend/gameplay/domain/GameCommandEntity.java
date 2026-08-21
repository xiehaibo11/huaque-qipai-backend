package com.nanbei.entertainment.backend.gameplay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "game_commands")
public class GameCommandEntity {
    @Id private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String requestHash;

    @Column(name = "command_type", nullable = false, length = 64)
    private String commandType;

    @Column(name = "expected_revision", nullable = false)
    private long expectedRevision;

    @Column(name = "accepted_revision")
    private Long acceptedRevision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String result;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GameCommandEntity() {}

    public GameCommandEntity(
            UUID sessionId,
            UUID userId,
            String idempotencyKey,
            String requestHash,
            String commandType,
            long expectedRevision,
            Instant occurredAt) {
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision must not be negative");
        }
        this.id = UUID.randomUUID();
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        this.requestHash = requireText(requestHash, "requestHash");
        this.commandType = requireText(commandType, "commandType");
        this.expectedRevision = expectedRevision;
        this.result = "{}";
        this.createdAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public void accept(long revision, String response) {
        if (acceptedRevision != null) {
            throw new IllegalStateException("command is already accepted");
        }
        if (revision <= 0) {
            throw new IllegalArgumentException("accepted revision must be positive");
        }
        acceptedRevision = revision;
        result = Objects.requireNonNull(response, "response");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public UUID getId() {
        return id;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Long getAcceptedRevision() {
        return acceptedRevision;
    }

    public String getResult() {
        return result;
    }
}
