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
@Table(name = "gold_membership_cards")
public class GoldMembershipCardEntity {
    @Id private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected GoldMembershipCardEntity() {}

    public GoldMembershipCardEntity(UUID userId, String productCode) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.productCode = productCode;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void activate(Duration duration, Instant now) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        Instant anchor = expiresAt != null && expiresAt.isAfter(now) ? expiresAt : now;
        if (startedAt == null || !isActiveAt(now)) {
            startedAt = now;
        }
        expiresAt = anchor.plus(duration);
    }

    public boolean isActiveAt(Instant now) {
        return expiresAt != null && expiresAt.isAfter(now);
    }

    public UUID getUserId() {
        return userId;
    }

    public String getProductCode() {
        return productCode;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
