package com.nanbei.entertainment.backend.room.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "room_games")
public class RoomGameEntity {
    @EmbeddedId private RoomGameId id;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    private String badge;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean enabled;

    protected RoomGameEntity() {}

    public RoomGameId getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadge() {
        return badge;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
