package com.nanbei.entertainment.backend.realname.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "real_name_failed_attempts")
public class RealNameFailedAttemptEntity {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "failed_at", nullable = false)
    private Instant failedAt;

    protected RealNameFailedAttemptEntity() {}

    public RealNameFailedAttemptEntity(UUID userId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.failedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getFailedAt() {
        return failedAt;
    }
}
