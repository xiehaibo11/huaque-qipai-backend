package com.nanbei.entertainment.backend.timeloginact.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * 一个时段奖励。{@code startSecond >= endSecond} 表示跨零点时段，与原版
 * {@code loginRewards[].startTime/endTime} 同语义。
 */
@Entity
@Table(name = "time_login_slots")
public class TimeLoginSlotEntity {
    @Id private UUID id;

    @Column(name = "activity_id", nullable = false)
    private UUID activityId;

    @Column(name = "slot_order", nullable = false)
    private int slotOrder;

    @Column(name = "start_second", nullable = false)
    private int startSecond;

    @Column(name = "end_second", nullable = false)
    private int endSecond;

    @Column(name = "reward_type", nullable = false, length = 32)
    private String rewardType;

    @Column(name = "reward_amount", nullable = false)
    private long rewardAmount;

    @Column(name = "reward_name", nullable = false, length = 64)
    private String rewardName;

    protected TimeLoginSlotEntity() {}

    public UUID getId() {
        return id;
    }

    public UUID getActivityId() {
        return activityId;
    }

    public int getSlotOrder() {
        return slotOrder;
    }

    public int getStartSecond() {
        return startSecond;
    }

    public int getEndSecond() {
        return endSecond;
    }

    public String getRewardType() {
        return rewardType;
    }

    public long getRewardAmount() {
        return rewardAmount;
    }

    public String getRewardName() {
        return rewardName;
    }
}
