package com.nanbei.entertainment.backend.fortune.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_fortune_treasures")
public class FortuneTreasureEntity {
    private static final Duration VALIDITY = Duration.ofHours(3);

    @Id private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "treasure_code", nullable = false, length = 32)
    private String treasureCode;

    @Column(nullable = false)
    private int level;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FortuneTreasureEntity() {}

    public FortuneTreasureEntity(UUID userId, String treasureCode, Instant occurredAt) {
        id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.treasureCode = requireText(treasureCode);
        level = 1;
        expiresAt = Objects.requireNonNull(occurredAt, "occurredAt").plus(VALIDITY);
        updatedAt = occurredAt;
    }

    public void refresh(Instant occurredAt) {
        if (expiresAt.isAfter(occurredAt)) {
            level = Math.min(10, level + 1);
        } else {
            level = 1;
        }
        expiresAt = occurredAt.plus(VALIDITY);
        updatedAt = occurredAt;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("treasureCode must not be blank");
        }
        return value;
    }

    public String getTreasureCode() {
        return treasureCode;
    }

    public int getLevel() {
        return level;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
