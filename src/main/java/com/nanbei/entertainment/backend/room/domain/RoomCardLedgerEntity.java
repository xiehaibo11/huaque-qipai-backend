package com.nanbei.entertainment.backend.room.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_card_ledger")
public class RoomCardLedgerEntity {
    @Id private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "amount_centi", nullable = false)
    private long amountCenti;

    @Column(nullable = false)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RoomCardLedgerEntity() {}

    public RoomCardLedgerEntity(UUID userId, UUID roomId, long amountCenti) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.roomId = roomId;
        this.amountCenti = amountCenti;
        this.reason = "ROOM_CREATE";
        this.createdAt = Instant.now();
    }
}
