package com.nanbei.entertainment.backend.mission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mission_pages")
public class MissionPageDefinitionEntity {
    @Id
    @Column(name = "page_code")
    private String pageCode;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "cycle_type", nullable = false)
    private MissionCycleType cycleType;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean enabled;

    protected MissionPageDefinitionEntity() {}

    public String getPageCode() { return pageCode; }
    public String getDisplayName() { return displayName; }
    public MissionCycleType getCycleType() { return cycleType; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isEnabled() { return enabled; }
}
