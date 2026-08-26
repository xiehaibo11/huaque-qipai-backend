package com.nanbei.entertainment.backend.membership.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "gold_membership_card_claims")
public class GoldMembershipCardClaimEntity {
    @Id private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Column(name = "claimed_on", nullable = false)
    private LocalDate claimedOn;

    @Column(nullable = false)
    private long coins;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GoldMembershipCardClaimEntity() {}

    public GoldMembershipCardClaimEntity(
            UUID userId, String productCode, LocalDate claimedOn, long coins) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.productCode = productCode;
        this.claimedOn = claimedOn;
        this.coins = coins;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public String getProductCode() {
        return productCode;
    }
}
