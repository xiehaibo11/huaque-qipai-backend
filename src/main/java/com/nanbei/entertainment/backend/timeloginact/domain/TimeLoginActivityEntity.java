package com.nanbei.entertainment.backend.timeloginact.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 定时登录活动配置。数值来源与边界见 V36 迁移注释。 */
@Entity
@Table(name = "time_login_activities")
public class TimeLoginActivityEntity {
    @Id private UUID id;

    @Column(name = "activity_code", nullable = false, length = 64)
    private String activityCode;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "gold_over", nullable = false)
    private long goldOver;

    @Column(name = "supplement_count", nullable = false)
    private int supplementCount;

    @Column(name = "wheel_unlock_count", nullable = false)
    private int wheelUnlockCount;

    @Column(name = "day_boundary_second", nullable = false)
    private int dayBoundarySecond;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TimeLoginActivityEntity() {}

    public UUID getId() {
        return id;
    }

    public String getActivityCode() {
        return activityCode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getGoldOver() {
        return goldOver;
    }

    public int getSupplementCount() {
        return supplementCount;
    }

    public int getWheelUnlockCount() {
        return wheelUnlockCount;
    }

    public int getDayBoundarySecond() {
        return dayBoundarySecond;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
