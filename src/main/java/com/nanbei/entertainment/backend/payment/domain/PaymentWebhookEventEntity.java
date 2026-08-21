package com.nanbei.entertainment.backend.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_webhook_events")
public class PaymentWebhookEventEntity {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProviderType provider;

    @Column(name = "provider_event_id", nullable = false)
    private String providerEventId;

    @Column(name = "payload_hash", nullable = false)
    private String payloadHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false)
    private WebhookProcessingStatus processingStatus;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected PaymentWebhookEventEntity() {}

    public PaymentWebhookEventEntity(
            PaymentProviderType provider,
            String providerEventId,
            String payloadHash,
            UUID orderId) {
        this.id = UUID.randomUUID();
        this.provider = provider;
        this.providerEventId = providerEventId;
        this.payloadHash = payloadHash;
        this.orderId = orderId;
        this.processingStatus = WebhookProcessingStatus.PROCESSED;
        this.receivedAt = Instant.now();
        this.processedAt = this.receivedAt;
    }

    public String getPayloadHash() {
        return payloadHash;
    }
}
