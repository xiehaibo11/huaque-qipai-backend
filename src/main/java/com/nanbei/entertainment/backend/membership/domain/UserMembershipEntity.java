package com.nanbei.entertainment.backend.membership.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_memberships")
public class UserMembershipEntity {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "membership_level", nullable = false)
    private int membershipLevel;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew;

    @Column(name = "last_order_id")
    private UUID lastOrderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected UserMembershipEntity() {}

    public UserMembershipEntity(UUID userId) {
        this.userId = userId;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void activate(
            int membershipLevel,
            Duration duration,
            boolean autoRenew,
            UUID orderId,
            Instant paidAt) {
        if (membershipLevel <= 0) {
            throw new IllegalArgumentException("membershipLevel must be positive");
        }
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        Instant anchor =
                expiresAt != null && expiresAt.isAfter(paidAt) ? expiresAt : paidAt;
        if (startedAt == null || !isActiveAt(paidAt)) {
            startedAt = paidAt;
        }
        this.membershipLevel = membershipLevel;
        this.expiresAt = anchor.plus(duration);
        this.autoRenew = autoRenew;
        this.lastOrderId = orderId;
    }

    public boolean isActiveAt(Instant now) {
        return membershipLevel > 0 && expiresAt != null && expiresAt.isAfter(now);
    }

    public UUID getUserId() {
        return userId;
    }

    public int getMembershipLevel() {
        return membershipLevel;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isAutoRenew() {
        return autoRenew;
    }

    public UUID getLastOrderId() {
        return lastOrderId;
    }
}
