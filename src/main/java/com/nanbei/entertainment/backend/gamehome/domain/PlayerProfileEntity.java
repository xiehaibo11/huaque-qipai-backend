package com.nanbei.entertainment.backend.gamehome.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "player_profiles")
public class PlayerProfileEntity {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "public_player_id", nullable = false, unique = true)
    private long publicPlayerId;

    @Column(name = "avatar_key", nullable = false, length = 120)
    private String avatarKey;

    @Column(name = "membership_level", nullable = false)
    private int membershipLevel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlayerProfileEntity() {}

    public PlayerProfileEntity(
            UUID userId,
            long publicPlayerId,
            String avatarKey,
            int membershipLevel) {
        this.userId = userId;
        this.publicPlayerId = publicPlayerId;
        this.avatarKey = avatarKey;
        this.membershipLevel = membershipLevel;
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

    public UUID getUserId() {
        return userId;
    }

    public long getPublicPlayerId() {
        return publicPlayerId;
    }

    public String getAvatarKey() {
        return avatarKey;
    }

    public void setAvatarKey(String avatarKey) {
        if (avatarKey == null || avatarKey.isBlank()) {
            throw new IllegalArgumentException("avatarKey must not be blank");
        }
        this.avatarKey = avatarKey;
    }

    public int getMembershipLevel() {
        return membershipLevel;
    }

    public void setMembershipLevel(int membershipLevel) {
        if (membershipLevel < 0) {
            throw new IllegalArgumentException("membershipLevel must not be negative");
        }
        this.membershipLevel = membershipLevel;
    }
}
