package com.nanbei.entertainment.backend.region.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_region_selections")
public class UserRegionSelectionEntity {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "lobby_id", nullable = false)
    private long lobbyId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserRegionSelectionEntity() {}

    public UserRegionSelectionEntity(UUID userId, long lobbyId) {
        this.userId = userId;
        this.lobbyId = lobbyId;
    }

    public void select(long selectedLobbyId) {
        lobbyId = selectedLobbyId;
    }

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public long getLobbyId() {
        return lobbyId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
