package com.nanbei.entertainment.backend.friend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "friendships")
@IdClass(FriendshipId.class)
public class FriendshipEntity {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "friend_id")
    private UUID friendId;

    @Column(name = "shielded", nullable = false)
    private boolean shielded;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FriendshipEntity() {}

    public FriendshipEntity(UUID userId, UUID friendId) {
        this.userId = userId;
        this.friendId = friendId;
        this.shielded = false;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFriendId() {
        return friendId;
    }

    public boolean isShielded() {
        return shielded;
    }

    public void setShielded(boolean shielded) {
        this.shielded = shielded;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
