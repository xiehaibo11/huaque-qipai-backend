package com.nanbei.entertainment.backend.goldroom.application;

import com.nanbei.entertainment.backend.goldroom.domain.GoldGameEntity;

/** A gold-room game entry for the lobby grid. */
public record GoldGameView(
        long lobbyId,
        long gameId,
        String displayName,
        Long boxGameId,
        int goldMode,
        int chairCount) {

    public static GoldGameView from(GoldGameEntity entity) {
        return new GoldGameView(
                entity.getId().getLobbyId(),
                entity.getId().getGameId(),
                entity.getDisplayName(),
                entity.getBoxGameId(),
                entity.getGoldMode(),
                entity.getChairCount());
    }
}
