package com.nanbei.entertainment.backend.mission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "mission_rewards")
public class MissionRewardEntity {
    @Id private UUID id;

    @Column(name = "task_code")
    private String taskCode;

    @Column(name = "page_code")
    private String pageCode;

    @Column(name = "milestone_target")
    private Long milestoneTarget;

    @Column(name = "reward_order", nullable = false)
    private int rewardOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "reward_type", nullable = false)
    private MissionRewardType rewardType;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "icon_key", nullable = false)
    private String iconKey;

    @Column(nullable = false)
    private long amount;

    protected MissionRewardEntity() {}

    public MissionRewardType getRewardType() { return rewardType; }
    public String getItemCode() { return itemCode; }
    public String getDisplayName() { return displayName; }
    public String getIconKey() { return iconKey; }
    public long getAmount() { return amount; }
}
