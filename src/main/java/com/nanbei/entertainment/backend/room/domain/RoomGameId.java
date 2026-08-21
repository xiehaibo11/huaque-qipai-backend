package com.nanbei.entertainment.backend.room.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class RoomGameId implements Serializable {
    @Column(name = "lobby_id")
    private long lobbyId;

    @Column(name = "game_id")
    private long gameId;

    protected RoomGameId() {}

    public RoomGameId(long lobbyId, long gameId) {
        this.lobbyId = lobbyId;
        this.gameId = gameId;
    }

    public long getLobbyId() {
        return lobbyId;
    }

    public long getGameId() {
        return gameId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RoomGameId that)) {
            return false;
        }
        return lobbyId == that.lobbyId && gameId == that.gameId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lobbyId, gameId);
    }
}
