package com.nanbei.entertainment.backend.freedraw.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "free_draw_prizes")
public class FreeDrawPrizeEntity {
    @Id private UUID id;

    @Column(name = "activity_id", nullable = false)
    private UUID activityId;

    @Column(name = "reward_type", nullable = false, length = 32)
    private String rewardType;

    @Column(name = "reward_amount", nullable = false)
    private long rewardAmount;

    @Column(name = "display_name", nullable = false, length = 64)
    private String displayName;

    @Column(name = "icon_key", nullable = false, length = 32)
    private String iconKey;

    @Column(nullable = false)
    private int weight;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean enabled;

    protected FreeDrawPrizeEntity() {}

    public FreeDrawPrizeEntity(
            UUID id,
            UUID activityId,
            String rewardType,
            long rewardAmount,
            String displayName,
            String iconKey,
            int weight,
            int displayOrder,
            boolean enabled) {
        this.id = id;
        this.activityId = activityId;
        this.rewardType = rewardType;
        this.rewardAmount = rewardAmount;
        this.displayName = displayName;
        this.iconKey = iconKey;
        this.weight = weight;
        this.displayOrder = displayOrder;
        this.enabled = enabled;
    }

    public UUID getId() {
        return id;
    }

    public String getRewardType() {
        return rewardType;
    }

    public long getRewardAmount() {
        return rewardAmount;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIconKey() {
        return iconKey;
    }

    public int getWeight() {
        return weight;
    }
}
