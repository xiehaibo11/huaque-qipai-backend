package com.nanbei.entertainment.backend.goldroom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** Composite key of a gold-room game: the region lobby plus the original ConfID. */
@Embeddable
public class GoldGameId implements Serializable {
    @Column(name = "lobby_id")
    private long lobbyId;

    @Column(name = "game_id")
    private long gameId;

    protected GoldGameId() {}

    public GoldGameId(long lobbyId, long gameId) {
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
        if (!(other instanceof GoldGameId that)) {
            return false;
        }
        return lobbyId == that.lobbyId && gameId == that.gameId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lobbyId, gameId);
    }
}
