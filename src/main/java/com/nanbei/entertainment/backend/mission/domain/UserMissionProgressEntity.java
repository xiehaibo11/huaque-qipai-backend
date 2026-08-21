package com.nanbei.entertainment.backend.mission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_mission_progress")
public class UserMissionProgressEntity {
    @Id private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "task_code", nullable = false)
    private String taskCode;

    @Column(name = "cycle_started_at", nullable = false)
    private Instant cycleStartedAt;

    @Column(nullable = false)
    private long target;

    @Column(nullable = false)
    private long progress;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected UserMissionProgressEntity() {}

    public UserMissionProgressEntity(
            UUID userId, String taskCode, Instant cycleStartedAt, long target) {
        if (target <= 0) throw new IllegalArgumentException("mission target must be positive");
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.taskCode = taskCode;
        this.cycleStartedAt = cycleStartedAt;
        this.target = target;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void increment(long amount) {
        if (amount <= 0) throw new IllegalArgumentException("mission progress must be positive");
        progress = Math.min(target, Math.addExact(progress, amount));
        updatedAt = Instant.now();
    }

    public void claim(Instant now) {
        if (!isComplete()) throw new IllegalStateException("mission is incomplete");
        if (claimedAt != null) throw new IllegalStateException("mission is already claimed");
        claimedAt = now;
        updatedAt = now;
    }

    public UUID getUserId() { return userId; }
    public String getTaskCode() { return taskCode; }
    public Instant getCycleStartedAt() { return cycleStartedAt; }
    public long getTarget() { return target; }
    public long getProgress() { return progress; }
    public Instant getClaimedAt() { return claimedAt; }
    public boolean isComplete() { return progress >= target; }
    public boolean isClaimed() { return claimedAt != null; }
}
