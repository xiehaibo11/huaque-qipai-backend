package com.nanbei.entertainment.backend.roomtools.domain;

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
@Table(name = "room_tool_operations")
public class RoomToolOperationEntity {
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

    @Column(name = "operation_type", nullable = false, length = 64)
    private String operationType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String result;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RoomToolOperationEntity() {}

    public RoomToolOperationEntity(
            UUID sessionId,
            UUID userId,
            String idempotencyKey,
            String requestHash,
            String operationType,
            String result,
            Instant occurredAt) {
        id = UUID.randomUUID();
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        this.requestHash = requireText(requestHash, "requestHash");
        this.operationType = requireText(operationType, "operationType");
        this.result = requireText(result, "result");
        createdAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getResult() {
        return result;
    }
}
