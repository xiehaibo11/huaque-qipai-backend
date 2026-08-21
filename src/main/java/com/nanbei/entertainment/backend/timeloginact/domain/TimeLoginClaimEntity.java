package com.nanbei.entertainment.backend.timeloginact.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * 一次已发放的领取。唯一性由 V36 的两个部分唯一索引保证：同一活动自然日内
 * 每个时段只能领一次，转盘只能抽一次。
 */
@Entity
@Table(name = "time_login_claims")
public class TimeLoginClaimEntity {
    public static final String TYPE_SLOT = "SLOT";
    public static final String TYPE_WHEEL = "WHEEL";

    @Id private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "activity_id", nullable = false)
    private UUID activityId;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "claim_type", nullable = false, length = 16)
    private String claimType;

    @Column(name = "slot_id")
    private UUID slotId;

    @Column(name = "wheel_slice_index")
    private Integer wheelSliceIndex;

    @Column(name = "reward_type", nullable = false, length = 32)
    private String rewardType;

    @Column(name = "reward_amount", nullable = false)
    private long rewardAmount;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt;

    protected TimeLoginClaimEntity() {}

    private TimeLoginClaimEntity(
            UUID userId,
            UUID activityId,
            LocalDate activityDate,
            String claimType,
            UUID slotId,
            Integer wheelSliceIndex,
            String rewardType,
            long rewardAmount,
            Instant claimedAt) {
        id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.activityId = Objects.requireNonNull(activityId, "activityId");
        this.activityDate = Objects.requireNonNull(activityDate, "activityDate");
        this.claimType = claimType;
        this.slotId = slotId;
        this.wheelSliceIndex = wheelSliceIndex;
        this.rewardType = Objects.requireNonNull(rewardType, "rewardType");
        this.rewardAmount = rewardAmount;
        this.claimedAt = Objects.requireNonNull(claimedAt, "claimedAt");
    }

    public static TimeLoginClaimEntity forSlot(
            UUID userId,
            UUID activityId,
            LocalDate activityDate,
            TimeLoginSlotEntity slot,
            Instant claimedAt) {
        return new TimeLoginClaimEntity(
                userId,
                activityId,
                activityDate,
                TYPE_SLOT,
                slot.getId(),
                null,
                slot.getRewardType(),
                slot.getRewardAmount(),
                claimedAt);
    }

    public static TimeLoginClaimEntity forWheel(
            UUID userId,
            UUID activityId,
            LocalDate activityDate,
            TimeLoginWheelSliceEntity slice,
            Instant claimedAt) {
        return new TimeLoginClaimEntity(
                userId,
                activityId,
                activityDate,
                TYPE_WHEEL,
                null,
                slice.getSliceIndex(),
                slice.getRewardType(),
                slice.getRewardAmount(),
                claimedAt);
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

    public LocalDate getActivityDate() {
        return activityDate;
    }

    public String getClaimType() {
        return claimType;
    }

    public UUID getSlotId() {
        return slotId;
    }

    public Integer getWheelSliceIndex() {
        return wheelSliceIndex;
    }

    public String getRewardType() {
        return rewardType;
    }

    public long getRewardAmount() {
        return rewardAmount;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public boolean isSlotClaim() {
        return TYPE_SLOT.equals(claimType);
    }
}
