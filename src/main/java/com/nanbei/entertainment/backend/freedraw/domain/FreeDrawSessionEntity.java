package com.nanbei.entertainment.backend.freedraw.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "free_draw_sessions")
public class FreeDrawSessionEntity {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_GRANTED = "GRANTED";

    @Id private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "activity_id", nullable = false)
    private UUID activityId;

    @Column(name = "draw_date", nullable = false)
    private LocalDate drawDate;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "granted_at")
    private Instant grantedAt;

    @Column(name = "reward_prize_id")
    private UUID rewardPrizeId;

    @Column(name = "reward_type", length = 32)
    private String rewardType;

    @Column(name = "reward_amount")
    private Long rewardAmount;

    @Column(name = "reward_name", length = 64)
    private String rewardName;

    @Column(name = "reward_icon_key", length = 32)
    private String rewardIconKey;

    @Column(name = "ad_source_id", length = 128)
    private String adSourceId;

    @Column(name = "ad_show_id", length = 128)
    private String adShowId;

    @Version
    @Column(nullable = false)
    private long version;

    protected FreeDrawSessionEntity() {}

    public static FreeDrawSessionEntity open(
            UUID id,
            UUID userId,
            UUID activityId,
            LocalDate drawDate,
            Instant createdAt,
            Instant expiresAt) {
        FreeDrawSessionEntity session = new FreeDrawSessionEntity();
        session.id = id;
        session.userId = userId;
        session.activityId = activityId;
        session.drawDate = drawDate;
        session.status = STATUS_PENDING;
        session.createdAt = createdAt;
        session.expiresAt = expiresAt;
        return session;
    }

    public void grant(
            FreeDrawPrizeEntity prize, String adSourceId, String adShowId, Instant grantedAt) {
        status = STATUS_GRANTED;
        rewardPrizeId = prize.getId();
        rewardType = prize.getRewardType();
        rewardAmount = prize.getRewardAmount();
        rewardName = prize.getDisplayName();
        rewardIconKey = prize.getIconKey();
        this.adSourceId = clipped(adSourceId);
        this.adShowId = clipped(adShowId);
        this.grantedAt = grantedAt;
    }

    private static String clipped(String value) {
        if (value == null) return "";
        return value.length() <= 128 ? value : value.substring(0, 128);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getActivityId() {
        return activityId;
    }

    public LocalDate getDrawDate() {
        return drawDate;
    }

    public String getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public UUID getRewardPrizeId() {
        return rewardPrizeId;
    }

    public String getRewardType() {
        return rewardType;
    }

    public long getRewardAmount() {
        return rewardAmount == null ? 0 : rewardAmount;
    }

    public String getRewardName() {
        return rewardName;
    }

    public String getRewardIconKey() {
        return rewardIconKey;
    }
}
