package com.nanbei.entertainment.backend.roomtools.domain;

import com.nanbei.entertainment.backend.roomtools.application.RoomMessageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "room_tool_messages")
public class RoomToolMessageEntity {
    @Id private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "sender_user_id", nullable = false)
    private UUID senderUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 32)
    private RoomMessageType messageType;

    @Column(name = "content_index")
    private Integer contentIndex;

    @Column(name = "voice_media_type", length = 32)
    private String voiceMediaType;

    @Column(name = "voice_duration_ms")
    private Integer voiceDurationMillis;

    @Column(name = "voice_data", columnDefinition = "bytea")
    private byte[] voiceData;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RoomToolMessageEntity() {}

    public static RoomToolMessageEntity quickPhrase(
            UUID sessionId, UUID userId, int index, Instant occurredAt) {
        return content(sessionId, userId, RoomMessageType.QUICK_PHRASE, index, occurredAt);
    }

    public static RoomToolMessageEntity emoji(
            UUID sessionId, UUID userId, int index, Instant occurredAt) {
        return content(sessionId, userId, RoomMessageType.EMOJI, index, occurredAt);
    }

    public static RoomToolMessageEntity voice(
            UUID sessionId,
            UUID userId,
            String mediaType,
            int durationMillis,
            byte[] data,
            Instant occurredAt) {
        RoomToolMessageEntity entity = base(sessionId, userId, RoomMessageType.VOICE, occurredAt);
        entity.voiceMediaType = Objects.requireNonNull(mediaType, "mediaType");
        entity.voiceDurationMillis = durationMillis;
        entity.voiceData = Arrays.copyOf(data, data.length);
        return entity;
    }

    private static RoomToolMessageEntity content(
            UUID sessionId,
            UUID userId,
            RoomMessageType type,
            int index,
            Instant occurredAt) {
        RoomToolMessageEntity entity = base(sessionId, userId, type, occurredAt);
        entity.contentIndex = index;
        return entity;
    }

    private static RoomToolMessageEntity base(
            UUID sessionId, UUID userId, RoomMessageType type, Instant occurredAt) {
        RoomToolMessageEntity entity = new RoomToolMessageEntity();
        entity.id = UUID.randomUUID();
        entity.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        entity.senderUserId = Objects.requireNonNull(userId, "userId");
        entity.messageType = Objects.requireNonNull(type, "type");
        entity.createdAt = Objects.requireNonNull(occurredAt, "occurredAt");
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getSenderUserId() {
        return senderUserId;
    }

    public RoomMessageType getMessageType() {
        return messageType;
    }

    public int getContentIndex() {
        return contentIndex == null ? -1 : contentIndex;
    }

    public String getVoiceMediaType() {
        return voiceMediaType;
    }

    public int getVoiceDurationMillis() {
        return voiceDurationMillis == null ? 0 : voiceDurationMillis;
    }

    public byte[] getVoiceData() {
        return voiceData == null ? new byte[0] : Arrays.copyOf(voiceData, voiceData.length);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
