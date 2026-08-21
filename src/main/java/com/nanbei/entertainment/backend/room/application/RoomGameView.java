package com.nanbei.entertainment.backend.room.application;

import com.nanbei.entertainment.backend.room.domain.RoomGameEntity;

public record RoomGameView(
        long lobbyId,
        long gameId,
        String displayName,
        String badge,
        int sortOrder) {
    static RoomGameView from(RoomGameEntity game) {
        return new RoomGameView(
                game.getId().getLobbyId(),
                game.getId().getGameId(),
                game.getDisplayName(),
                game.getBadge(),
                game.getSortOrder());
    }
}
