package com.nanbei.entertainment.backend.fortune.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_fortune_states")
public class FortuneStateEntity {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "wealth_points", nullable = false)
    private int wealthPoints;

    @Column(name = "luck_points", nullable = false)
    private int luckPoints;

    @Column(name = "caishen_expires_at")
    private Instant caishenExpiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FortuneStateEntity() {}

    public FortuneStateEntity(UUID userId, Instant occurredAt) {
        this.userId = Objects.requireNonNull(userId, "userId");
        updatedAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public void addProgress(int wealth, int luck, Instant occurredAt) {
        if (wealth < 0 || luck < 0) {
            throw new IllegalArgumentException("fortune progress must not be negative");
        }
        wealthPoints = Math.addExact(wealthPoints, wealth);
        luckPoints = Math.addExact(luckPoints, luck);
        updatedAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public void activateCaishen(Instant occurredAt, long durationSeconds) {
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("durationSeconds must be positive");
        }
        Instant base =
                caishenExpiresAt != null && caishenExpiresAt.isAfter(occurredAt)
                        ? caishenExpiresAt
                        : occurredAt;
        caishenExpiresAt = base.plusSeconds(durationSeconds);
        updatedAt = occurredAt;
    }

    public int getWealthPoints() {
        return wealthPoints;
    }

    public int getLuckPoints() {
        return luckPoints;
    }

    public Instant getCaishenExpiresAt() {
        return caishenExpiresAt;
    }
}
