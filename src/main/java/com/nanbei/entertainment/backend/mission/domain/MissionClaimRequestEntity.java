package com.nanbei.entertainment.backend.mission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "mission_claim_requests")
public class MissionClaimRequestEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "idempotency_key", nullable = false) private String idempotencyKey;
    @Column(name = "claim_type", nullable = false) private String claimType;
    @Column(name = "claim_reference", nullable = false) private String claimReference;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private String responsePayload;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;

    protected MissionClaimRequestEntity() {}

    public MissionClaimRequestEntity(
            UUID userId, String idempotencyKey, String claimType, String claimReference, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.claimType = claimType;
        this.claimReference = claimReference;
        this.createdAt = now;
    }

    public boolean matches(String type, String reference) {
        return claimType.equals(type) && claimReference.equals(reference);
    }

    public void complete(String payload, Instant now) {
        responsePayload = payload;
        completedAt = now;
    }

    public String getResponsePayload() { return responsePayload; }
}
