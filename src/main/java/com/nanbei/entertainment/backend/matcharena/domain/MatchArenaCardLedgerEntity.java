package com.nanbei.entertainment.backend.matcharena.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match_arena_card_ledger")
public class MatchArenaCardLedgerEntity {
    @Id private UUID id;

    @Column(name = "arena_id", nullable = false)
    private UUID arenaId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "amount_centi", nullable = false)
    private long amountCenti;

    @Column(nullable = false, length = 24)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MatchArenaCardLedgerEntity() {}

    public MatchArenaCardLedgerEntity(UUID arenaId, UUID userId, long roomCards) {
        id = UUID.randomUUID();
        this.arenaId = arenaId;
        this.userId = userId;
        amountCenti = Math.multiplyExact(roomCards, 100L);
        reason = "INITIAL_FUNDING";
        createdAt = Instant.now();
    }
}
