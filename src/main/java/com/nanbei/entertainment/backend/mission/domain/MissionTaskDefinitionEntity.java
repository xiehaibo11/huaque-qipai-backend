package com.nanbei.entertainment.backend.mission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "mission_task_definitions")
public class MissionTaskDefinitionEntity {
    @Id
    @Column(name = "task_code")
    private String taskCode;

    @Column(name = "page_code", nullable = false)
    private String pageCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private MissionEventType eventType;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private long target;

    @Column(name = "activity_points", nullable = false)
    private long activityPoints;

    @Column(name = "jump_type", nullable = false)
    private String jumpType;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean enabled;

    /** 限时任务窗口；为空表示完全跟随页签周期。 */
    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    /** 活动结束后仍可领奖的宽限截止，对应 View.lua 的 drawDeadline。 */
    @Column(name = "draw_deadline")
    private Instant drawDeadline;

    protected MissionTaskDefinitionEntity() {}

    public String getTaskCode() { return taskCode; }
    public String getPageCode() { return pageCode; }
    public MissionEventType getEventType() { return eventType; }
    public String getDisplayName() { return displayName; }
    public long getTarget() { return target; }
    public long getActivityPoints() { return activityPoints; }
    public String getJumpType() { return jumpType; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isEnabled() { return enabled; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Instant getDrawDeadline() { return drawDeadline; }
}
