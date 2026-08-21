package com.nanbei.entertainment.backend.membership.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "membership_daily_gift_claims")
public class MembershipDailyGiftClaimEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "claimed_on", nullable = false)
    private LocalDate claimedOn;

    @Column(name = "gift_id", nullable = false)
    private int giftId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String rewards;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MembershipDailyGiftClaimEntity() {}

    public MembershipDailyGiftClaimEntity(
            UUID userId,
            LocalDate claimedOn,
            int giftId,
            String rewards) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.claimedOn = claimedOn;
        this.giftId = giftId;
        this.rewards = rewards;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getClaimedOn() {
        return claimedOn;
    }

    public int getGiftId() {
        return giftId;
    }

    public String getRewards() {
        return rewards;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
