package com.nanbei.entertainment.backend.room.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_participants")
public class RoomParticipantEntity {
    @EmbeddedId private RoomParticipantId id;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected RoomParticipantEntity() {}

    public RoomParticipantEntity(UUID roomId, UUID userId) {
        this.id = new RoomParticipantId(roomId, userId);
        this.joinedAt = Instant.now();
    }

    public UUID getUserId() {
        return id.getUserId();
    }
}
