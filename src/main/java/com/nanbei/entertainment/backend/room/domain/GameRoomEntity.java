package com.nanbei.entertainment.backend.room.domain;

import com.nanbei.entertainment.backend.room.application.RoomPayType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "game_rooms")
public class GameRoomEntity {
    @Id private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
            name = "room_number",
            nullable = false,
            unique = true,
            length = 6,
            columnDefinition = "char(6)")
    private String roomNumber;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "lobby_id", nullable = false)
    private long lobbyId;

    @Column(name = "game_id", nullable = false)
    private long gameId;

    @Column(name = "game_rule", nullable = false, columnDefinition = "text")
    private String gameRule;

    @Column(name = "game_rule_display", nullable = false, columnDefinition = "text")
    private String gameRuleDisplay;

    @Column(name = "room_rule", nullable = false, columnDefinition = "text")
    private String roomRule;

    @Column(name = "room_mode", nullable = false)
    private int roomMode;

    @Column(name = "player_count", nullable = false)
    private int playerCount;

    @Column(name = "play_count", nullable = false)
    private int playCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_type", nullable = false)
    private RoomPayType payType;

    @Column(name = "room_fee_centi", nullable = false)
    private int roomFeeCenti;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomStatus status;

    @Column(name = "creation_idempotency_key")
    private String creationIdempotencyKey;

    @Column(name = "creation_request_hash")
    private String creationRequestHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "first_round_at")
    private Instant firstRoundAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected GameRoomEntity() {}

    public GameRoomEntity(
            String roomNumber,
            UUID ownerUserId,
            long lobbyId,
            long gameId,
            String gameRule,
            String gameRuleDisplay,
            String roomRule,
            int roomMode,
            int playerCount,
            int playCount,
            RoomPayType payType,
            int roomFeeCenti,
            String creationIdempotencyKey,
            String creationRequestHash) {
        this.id = UUID.randomUUID();
        this.roomNumber = roomNumber;
        this.ownerUserId = ownerUserId;
        this.lobbyId = lobbyId;
        this.gameId = gameId;
        this.gameRule = gameRule;
        this.gameRuleDisplay = gameRuleDisplay;
        this.roomRule = roomRule;
        this.roomMode = roomMode;
        this.playerCount = playerCount;
        this.playCount = playCount;
        this.payType = payType;
        this.roomFeeCenti = roomFeeCenti;
        this.status = RoomStatus.OPEN;
        this.creationIdempotencyKey = creationIdempotencyKey;
        this.creationRequestHash = creationRequestHash;
        this.createdAt = Instant.now();
    }

    public void markFirstRound(Instant occurredAt) {
        if (status == RoomStatus.OPEN) {
            status = RoomStatus.CHARGED;
            firstRoundAt = occurredAt;
        }
    }

    public void dissolve(Instant occurredAt) {
        if (status == RoomStatus.OPEN || status == RoomStatus.CHARGED) {
            status = RoomStatus.DISSOLVED;
            closedAt = occurredAt;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public long getLobbyId() {
        return lobbyId;
    }

    public long getGameId() {
        return gameId;
    }

    public String getGameRule() {
        return gameRule;
    }

    public String getGameRuleDisplay() {
        return gameRuleDisplay;
    }

    public String getRoomRule() {
        return roomRule;
    }

    public int getRoomMode() {
        return roomMode;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public int getPlayCount() {
        return playCount;
    }

    public RoomPayType getPayType() {
        return payType;
    }

    public int getRoomFeeCenti() {
        return roomFeeCenti;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public String getCreationRequestHash() {
        return creationRequestHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFirstRoundAt() {
        return firstRoundAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
