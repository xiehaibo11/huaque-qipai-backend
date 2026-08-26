package com.nanbei.entertainment.backend.gamehome.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "lobby_announcements")
public class LobbyAnnouncementEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "lobby_id")
    private Long lobbyId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LobbyAnnouncementEntity() {}

    public LobbyAnnouncementEntity(
            String content,
            Long lobbyId,
            int sortOrder,
            boolean enabled,
            Instant startsAt,
            Instant endsAt) {
        this.content = content;
        this.lobbyId = lobbyId;
        this.sortOrder = sortOrder;
        this.enabled = enabled;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public boolean isVisibleTo(long selectedLobbyId, Instant now) {
        return enabled
                && (lobbyId == null || lobbyId == selectedLobbyId)
                && (startsAt == null || !startsAt.isAfter(now))
                && (endsAt == null || endsAt.isAfter(now));
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getContent() {
        return content;
    }
}
