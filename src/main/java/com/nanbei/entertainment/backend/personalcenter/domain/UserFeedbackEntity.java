package com.nanbei.entertainment.backend.personalcenter.domain;

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
@Table(name = "user_feedback")
public class UserFeedbackEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackCategory category;

    @Column(nullable = false, length = 500)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserFeedbackEntity() {}

    public UserFeedbackEntity(
            UUID userId,
            FeedbackCategory category,
            String content) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.category = category;
        this.content = content;
        this.status = FeedbackStatus.SUBMITTED;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void initializeTimestamp() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public FeedbackCategory getCategory() {
        return category;
    }

    public String getContent() {
        return content;
    }

    public FeedbackStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
