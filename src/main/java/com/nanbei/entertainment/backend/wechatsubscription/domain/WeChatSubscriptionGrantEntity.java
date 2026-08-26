package com.nanbei.entertainment.backend.wechatsubscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "wechat_subscription_grants")
public class WeChatSubscriptionGrantEntity {
    @Id private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "template_id", nullable = false, length = 128)
    private String templateId;

    @Column(nullable = false)
    private int scene;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
            name = "reserved_hash",
            nullable = false,
            length = 64,
            unique = true,
            columnDefinition = "char(64)")
    private String reservedHash;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
            name = "openid_subject_hash",
            nullable = false,
            length = 64,
            columnDefinition = "char(64)")
    private String openIdSubjectHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private WeChatSubscriptionGrantStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected WeChatSubscriptionGrantEntity() {}

    public WeChatSubscriptionGrantEntity(
            UUID userId,
            String templateId,
            int scene,
            String reservedHash,
            String openIdSubjectHash,
            Instant expiresAt,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.templateId = required(templateId, "templateId");
        this.scene = scene;
        this.reservedHash = hash(reservedHash, "reservedHash");
        this.openIdSubjectHash = hash(openIdSubjectHash, "openIdSubjectHash");
        this.status = WeChatSubscriptionGrantStatus.PENDING;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = createdAt;
    }

    public void accept(Instant occurredAt) {
        if (status != WeChatSubscriptionGrantStatus.PENDING) {
            return;
        }
        status = WeChatSubscriptionGrantStatus.AVAILABLE;
        confirmedAt = occurredAt;
        updatedAt = occurredAt;
    }

    public void deny(Instant occurredAt) {
        finishPending(WeChatSubscriptionGrantStatus.DENIED, occurredAt);
    }

    public void cancel(Instant occurredAt) {
        finishPending(WeChatSubscriptionGrantStatus.CANCELLED, occurredAt);
    }

    public void expire(Instant occurredAt) {
        finishPending(WeChatSubscriptionGrantStatus.EXPIRED, occurredAt);
    }

    public void claim(Instant occurredAt) {
        if (status != WeChatSubscriptionGrantStatus.AVAILABLE) {
            throw new IllegalStateException("subscription grant is unavailable");
        }
        status = WeChatSubscriptionGrantStatus.CLAIMED;
        claimedAt = occurredAt;
        updatedAt = occurredAt;
    }

    public void sent(Instant occurredAt) {
        if (status != WeChatSubscriptionGrantStatus.CLAIMED) {
            throw new IllegalStateException("subscription grant is not claimed");
        }
        status = WeChatSubscriptionGrantStatus.SENT;
        sentAt = occurredAt;
        updatedAt = occurredAt;
    }

    public void terminal(Instant occurredAt) {
        if (status == WeChatSubscriptionGrantStatus.SENT) {
            return;
        }
        status = WeChatSubscriptionGrantStatus.TERMINAL;
        updatedAt = occurredAt;
    }

    public void invalidate(Instant occurredAt) {
        if (status == WeChatSubscriptionGrantStatus.SENT
                || status == WeChatSubscriptionGrantStatus.TERMINAL) {
            return;
        }
        status = WeChatSubscriptionGrantStatus.INVALIDATED;
        updatedAt = occurredAt;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    private void finishPending(
            WeChatSubscriptionGrantStatus target, Instant occurredAt) {
        if (status != WeChatSubscriptionGrantStatus.PENDING) {
            return;
        }
        status = target;
        confirmedAt = occurredAt;
        updatedAt = occurredAt;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String hash(String value, String name) {
        if (value == null || value.length() != 64) {
            throw new IllegalArgumentException(name + " must be SHA-256");
        }
        return value;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTemplateId() {
        return templateId;
    }

    public int getScene() {
        return scene;
    }

    public String getReservedHash() {
        return reservedHash;
    }

    public String getOpenIdSubjectHash() {
        return openIdSubjectHash;
    }

    public WeChatSubscriptionGrantStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }
}
