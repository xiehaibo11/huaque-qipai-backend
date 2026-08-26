package com.nanbei.entertainment.backend.wechatpush.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wechat_push_events")
public class WeChatPushEventEntity {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String fingerprint;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected WeChatPushEventEntity() {}

    public WeChatPushEventEntity(String fingerprint, String eventType) {
        this.id = UUID.randomUUID();
        this.fingerprint = fingerprint;
        this.eventType = eventType;
        this.receivedAt = Instant.now();
    }
}
