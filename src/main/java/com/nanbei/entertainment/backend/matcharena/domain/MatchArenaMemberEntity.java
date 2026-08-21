package com.nanbei.entertainment.backend.matcharena.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match_arena_members")
public class MatchArenaMemberEntity {
    @Id private UUID id;

    @Column(name = "arena_id", nullable = false)
    private UUID arenaId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MatchArenaMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MatchArenaMemberStatus status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MatchArenaMemberEntity() {}

    public MatchArenaMemberEntity(UUID arenaId, UUID userId, MatchArenaMemberRole role) {
        id = UUID.randomUUID();
        this.arenaId = arenaId;
        this.userId = userId;
        this.role = role;
        status = MatchArenaMemberStatus.ACTIVE;
        joinedAt = Instant.now();
        updatedAt = joinedAt;
    }

    public UUID getArenaId() { return arenaId; }
    public MatchArenaMemberRole getRole() { return role; }
}
