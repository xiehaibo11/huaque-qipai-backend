package com.nanbei.entertainment.backend.timeloginact.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * 转盘的一格。{@code sliceIndex} 0 基，与原版 {@code TimeLoginActWheelLayer.csb} 的
 * {@code _KW_ITEM_1.._KW_ITEM_8} 一一对应；{@code weight} 只在服务端使用，不下发客户端。
 */
@Entity
@Table(name = "time_login_wheel_slices")
public class TimeLoginWheelSliceEntity {
    @Id private UUID id;

    @Column(name = "activity_id", nullable = false)
    private UUID activityId;

    @Column(name = "slice_index", nullable = false)
    private int sliceIndex;

    @Column(name = "reward_type", nullable = false, length = 32)
    private String rewardType;

    @Column(name = "reward_amount", nullable = false)
    private long rewardAmount;

    @Column(name = "reward_name", nullable = false, length = 64)
    private String rewardName;

    @Column(nullable = false)
    private int weight;

    protected TimeLoginWheelSliceEntity() {}

    public UUID getId() {
        return id;
    }

    public UUID getActivityId() {
        return activityId;
    }

    public int getSliceIndex() {
        return sliceIndex;
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

    public int getWeight() {
        return weight;
    }
}
