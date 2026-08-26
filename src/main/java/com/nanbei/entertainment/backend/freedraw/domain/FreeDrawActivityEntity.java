package com.nanbei.entertainment.backend.freedraw.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "free_draw_activities")
public class FreeDrawActivityEntity {
    @Id private UUID id;

    @Column(name = "activity_code", nullable = false, unique = true, length = 64)
    private String activityCode;

    @Column(name = "ad_placement_id", nullable = false, length = 64)
    private String adPlacementId;

    @Column(name = "provider_source_id", nullable = false, length = 64)
    private String providerSourceId;

    @Column(name = "daily_limit", nullable = false)
    private int dailyLimit;

    @Column(nullable = false)
    private boolean enabled;

    protected FreeDrawActivityEntity() {}

    public FreeDrawActivityEntity(
            UUID id,
            String activityCode,
            String adPlacementId,
            String providerSourceId,
            int dailyLimit,
            boolean enabled) {
        this.id = id;
        this.activityCode = activityCode;
        this.adPlacementId = adPlacementId;
        this.providerSourceId = providerSourceId;
        this.dailyLimit = dailyLimit;
        this.enabled = enabled;
    }

    public UUID getId() {
        return id;
    }

    public String getActivityCode() {
        return activityCode;
    }

    public String getAdPlacementId() {
        return adPlacementId;
    }

    public String getProviderSourceId() {
        return providerSourceId;
    }

    public int getDailyLimit() {
        return dailyLimit;
    }
}
