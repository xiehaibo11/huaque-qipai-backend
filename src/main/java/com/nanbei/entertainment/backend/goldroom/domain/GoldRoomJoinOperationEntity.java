package com.nanbei.entertainment.backend.goldroom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Idempotency ledger for modern gold-room matching requests. */
@Entity
@Table(name = "gold_room_join_operations")
public class GoldRoomJoinOperationEntity {
    @Id private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String requestHash;

    @Column(name = "lobby_id", nullable = false)
    private long lobbyId;

    @Column(name = "game_id", nullable = false)
    private long gameId;

    @Column(name = "room_name_flag", nullable = false)
    private int roomNameFlag;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String result;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GoldRoomJoinOperationEntity() {}

    public GoldRoomJoinOperationEntity(
            UUID userId,
            String idempotencyKey,
            String requestHash,
            long lobbyId,
            long gameId,
            int roomNameFlag,
            String result,
            Instant occurredAt) {
        id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        this.requestHash = requireText(requestHash, "requestHash");
        this.lobbyId = lobbyId;
        this.gameId = gameId;
        this.roomNameFlag = roomNameFlag;
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
