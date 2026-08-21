package com.nanbei.entertainment.backend.matcharena.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "match_arenas")
public class MatchArenaEntity {
    @Id private UUID id;

    @Column(name = "arena_number", nullable = false, unique = true)
    private int arenaNumber;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "lobby_id", nullable = false)
    private long lobbyId;

    @Column(nullable = false, length = 4)
    private String remark;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MatchArenaLevel level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private MatchArenaMode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_type", nullable = false, length = 16)
    private MatchArenaCostType costType;

    @Column(name = "original_pay_type", nullable = false)
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int originalPayType;

    @Column(name = "daily_room_card_limit", nullable = false)
    private long dailyRoomCardLimit;

    @Column(name = "room_card_centi", nullable = false)
    private long roomCardCenti;

    @Column(name = "visible_to_strangers", nullable = false)
    private boolean visibleToStrangers;

    @Column(name = "auto_transfer_enabled", nullable = false)
    private boolean autoTransferEnabled;

    @Column(name = "auto_transfer_threshold", nullable = false)
    private long autoTransferThreshold;

    @Column(name = "auto_transfer_amount", nullable = false)
    private long autoTransferAmount;

    @Column(name = "low_card_reminder_threshold")
    private Long lowCardReminderThreshold;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MatchArenaStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String requestHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected MatchArenaEntity() {}

    public MatchArenaEntity(
            int arenaNumber,
            UUID ownerUserId,
            long lobbyId,
            String remark,
            MatchArenaLevel level,
            MatchArenaMode mode,
            MatchArenaCostType costType,
            int originalPayType,
            long dailyRoomCardLimit,
            long initialRoomCards,
            boolean visibleToStrangers,
            boolean autoTransferEnabled,
            long autoTransferThreshold,
            long autoTransferAmount,
            Long lowCardReminderThreshold,
            String idempotencyKey,
            String requestHash) {
        id = UUID.randomUUID();
        this.arenaNumber = arenaNumber;
        this.ownerUserId = ownerUserId;
        this.lobbyId = lobbyId;
        this.remark = remark == null ? "" : remark.trim();
        this.level = level;
        this.mode = mode;
        this.costType = costType;
        this.originalPayType = originalPayType;
        this.dailyRoomCardLimit = dailyRoomCardLimit;
        roomCardCenti = Math.multiplyExact(initialRoomCards, 100L);
        this.visibleToStrangers = visibleToStrangers;
        this.autoTransferEnabled = autoTransferEnabled;
        this.autoTransferThreshold = autoTransferThreshold;
        this.autoTransferAmount = autoTransferAmount;
        this.lowCardReminderThreshold = lowCardReminderThreshold;
        status = MatchArenaStatus.OPEN;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public int getArenaNumber() { return arenaNumber; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public long getLobbyId() { return lobbyId; }
    public String getRemark() { return remark; }
    public MatchArenaLevel getLevel() { return level; }
    public MatchArenaMode getMode() { return mode; }
    public MatchArenaCostType getCostType() { return costType; }
    public int getOriginalPayType() { return originalPayType; }
    public long getDailyRoomCardLimit() { return dailyRoomCardLimit; }
    public long getRoomCards() { return roomCardCenti / 100L; }
    public boolean isVisibleToStrangers() { return visibleToStrangers; }
    public boolean isAutoTransferEnabled() { return autoTransferEnabled; }
    public long getAutoTransferThreshold() { return autoTransferThreshold; }
    public long getAutoTransferAmount() { return autoTransferAmount; }
    public Long getLowCardReminderThreshold() { return lowCardReminderThreshold; }
    public MatchArenaStatus getStatus() { return status; }
    public String getRequestHash() { return requestHash; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
