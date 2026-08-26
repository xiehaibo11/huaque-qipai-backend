package com.nanbei.entertainment.backend.wechatsubscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wechat_subscription_deliveries")
public class WeChatSubscriptionDeliveryEntity {
    @Id private UUID id;

    @Column(name = "grant_id", nullable = false, unique = true)
    private UUID grantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "template_id", nullable = false, length = 128)
    private String templateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "event_id", nullable = false, length = 160)
    private String eventId;

    @Column(nullable = false, length = 15)
    private String title;

    @Column(nullable = false, length = 200)
    private String content;

    @Column(name = "target_url", length = 500)
    private String targetUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private WeChatSubscriptionDeliveryStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_provider_code")
    private Integer lastProviderCode;

    @Column(name = "last_failure_class", length = 32)
    private String lastFailureClass;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Version private long version;

    protected WeChatSubscriptionDeliveryEntity() {}

    public WeChatSubscriptionDeliveryEntity(
            UUID grantId,
            UUID userId,
            String templateId,
            String eventType,
            String eventId,
            String title,
            String content,
            String targetUrl,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.grantId = required(grantId, "grantId");
        this.userId = required(userId, "userId");
        this.templateId = text(templateId, 128, "templateId");
        this.eventType = text(eventType, 64, "eventType");
        this.eventId = text(eventId, 160, "eventId");
        this.title = text(title, 15, "title");
        this.content = text(content, 200, "content");
        this.targetUrl = optional(targetUrl, 500, "targetUrl");
        this.status = WeChatSubscriptionDeliveryStatus.PENDING;
        this.createdAt = required(createdAt, "createdAt");
        this.updatedAt = createdAt;
    }

    public void start(Instant occurredAt) {
        if (status != WeChatSubscriptionDeliveryStatus.PENDING
                && status != WeChatSubscriptionDeliveryStatus.RETRYABLE) {
            throw new IllegalStateException("delivery is not pending");
        }
        status = WeChatSubscriptionDeliveryStatus.SENDING;
        attempts++;
        updatedAt = occurredAt;
    }

    public void sent(Instant occurredAt) {
        finish(WeChatSubscriptionDeliveryStatus.SENT, null, null, occurredAt);
        sentAt = occurredAt;
    }

    public void retryable(
            Integer providerCode, String failureClass, Instant nextAttemptAt) {
        finish(
                WeChatSubscriptionDeliveryStatus.RETRYABLE,
                providerCode,
                failureClass,
                nextAttemptAt);
        this.nextAttemptAt = nextAttemptAt;
    }

    public void terminal(
            Integer providerCode, String failureClass, Instant occurredAt) {
        finish(
                WeChatSubscriptionDeliveryStatus.TERMINAL,
                providerCode,
                failureClass,
                occurredAt);
    }

    public void ambiguous(Instant occurredAt) {
        finish(
                WeChatSubscriptionDeliveryStatus.AMBIGUOUS,
                null,
                "NETWORK_AMBIGUOUS",
                occurredAt);
    }

    private void finish(
            WeChatSubscriptionDeliveryStatus target,
            Integer providerCode,
            String failureClass,
            Instant occurredAt) {
        if (status != WeChatSubscriptionDeliveryStatus.SENDING) {
            throw new IllegalStateException("delivery is not sending");
        }
        status = target;
        lastProviderCode = providerCode;
        lastFailureClass = failureClass;
        updatedAt = occurredAt;
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String text(String value, int maxLength, String name) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String optional(String value, int maxLength, String name) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value == null || value.isBlank() ? null : value;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGrantId() {
        return grantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getEventId() {
        return eventId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public WeChatSubscriptionDeliveryStatus getStatus() {
        return status;
    }
}
