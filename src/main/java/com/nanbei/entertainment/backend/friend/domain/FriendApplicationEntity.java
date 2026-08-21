package com.nanbei.entertainment.backend.friend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "friend_applications")
public class FriendApplicationEntity {
    @Id
    private UUID id;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private FriendApplicationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "handled_at")
    private Instant handledAt;

    protected FriendApplicationEntity() {}

    public FriendApplicationEntity(UUID requesterId, UUID targetId) {
        this.id = UUID.randomUUID();
        this.requesterId = requesterId;
        this.targetId = targetId;
        this.status = FriendApplicationStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void accept(Instant handledAt) {
        status = FriendApplicationStatus.ACCEPTED;
        this.handledAt = handledAt;
    }

    public void reject(Instant handledAt) {
        status = FriendApplicationStatus.REJECTED;
        this.handledAt = handledAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequesterId() {
        return requesterId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public FriendApplicationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getHandledAt() {
        return handledAt;
    }
}
