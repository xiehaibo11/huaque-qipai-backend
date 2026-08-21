package com.nanbei.entertainment.backend.mission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mission_milestone_claims")
public class MissionMilestoneClaimEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "page_code", nullable = false) private String pageCode;
    @Column(name = "cycle_started_at", nullable = false) private Instant cycleStartedAt;
    @Column(nullable = false) private long target;
    @Column(name = "claimed_at", nullable = false) private Instant claimedAt;

    protected MissionMilestoneClaimEntity() {}

    public MissionMilestoneClaimEntity(
            UUID userId, String pageCode, Instant cycleStartedAt, long target, Instant claimedAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.pageCode = pageCode;
        this.cycleStartedAt = cycleStartedAt;
        this.target = target;
        this.claimedAt = claimedAt;
    }

    public long getTarget() { return target; }
}
