package com.nanbei.entertainment.backend.mail.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "mails")
public class MailEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 500)
    private String intro;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, length = 100)
    private String sender;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String attachments;

    @Column(name = "send_at", nullable = false)
    private Instant sendAt;

    @Column(name = "expire_at")
    private Instant expireAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MailEntity() {}

    public MailEntity(
            UUID userId,
            String title,
            String intro,
            String content,
            String sender,
            String attachments,
            Instant sendAt,
            Instant expireAt) {
        this.userId = userId;
        this.title = title;
        this.intro = intro == null ? "" : intro;
        this.content = content == null ? "" : content;
        this.sender = sender == null ? "" : sender;
        this.attachments = attachments == null ? "[]" : attachments;
        this.sendAt = sendAt;
        this.expireAt = expireAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public boolean isExpired(Instant now) {
        return expireAt != null && !expireAt.isAfter(now);
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public void markClaimed(Instant now) {
        if (claimedAt == null) {
            claimedAt = now;
        }
    }

    public void markDeleted(Instant now) {
        if (deletedAt == null) {
            deletedAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getIntro() {
        return intro;
    }

    public String getContent() {
        return content;
    }

    public String getSender() {
        return sender;
    }

    public String getAttachments() {
        return attachments;
    }

    public Instant getSendAt() {
        return sendAt;
    }

    public Instant getExpireAt() {
        return expireAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
