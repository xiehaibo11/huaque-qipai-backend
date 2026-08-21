package com.nanbei.entertainment.backend.mission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "mission_milestone_definitions")
public class MissionMilestoneDefinitionEntity {
    @Id private UUID id;

    @Column(name = "page_code", nullable = false)
    private String pageCode;

    @Column(nullable = false)
    private long target;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean enabled;

    protected MissionMilestoneDefinitionEntity() {}

    public String getPageCode() { return pageCode; }
    public long getTarget() { return target; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isEnabled() { return enabled; }
}
