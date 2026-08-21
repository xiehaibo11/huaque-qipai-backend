package com.nanbei.entertainment.backend.goldroom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** Composite key of a gold-room level: the game plus the original {@code roomnameflag}. */
@Embeddable
public class GoldGameLevelId implements Serializable {
    @Column(name = "lobby_id")
    private long lobbyId;

    @Column(name = "game_id")
    private long gameId;

    @Column(name = "room_name_flag")
    private int roomNameFlag;

    protected GoldGameLevelId() {}

    public GoldGameLevelId(long lobbyId, long gameId, int roomNameFlag) {
        this.lobbyId = lobbyId;
        this.gameId = gameId;
        this.roomNameFlag = roomNameFlag;
    }

    public long getLobbyId() {
        return lobbyId;
    }

    public long getGameId() {
        return gameId;
    }

    public int getRoomNameFlag() {
        return roomNameFlag;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GoldGameLevelId that)) {
            return false;
        }
        return lobbyId == that.lobbyId
                && gameId == that.gameId
                && roomNameFlag == that.roomNameFlag;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lobbyId, gameId, roomNameFlag);
    }
}
