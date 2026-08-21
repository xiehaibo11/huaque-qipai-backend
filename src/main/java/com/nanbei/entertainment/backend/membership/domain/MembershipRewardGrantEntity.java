package com.nanbei.entertainment.backend.membership.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "membership_reward_grants")
public class MembershipRewardGrantEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private String sourceId;

    @Column(name = "reward_code", nullable = false)
    private String rewardCode;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "duration_days")
    private Integer durationDays;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    protected MembershipRewardGrantEntity() {}

    public MembershipRewardGrantEntity(
            UUID userId,
            String sourceType,
            String sourceId,
            String rewardCode,
            String displayName,
            long quantity,
            Integer durationDays,
            String metadata) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.rewardCode = rewardCode;
        this.displayName = displayName;
        this.quantity = quantity;
        this.durationDays = durationDays;
        this.metadata = metadata;
        this.grantedAt = Instant.now();
    }
}
