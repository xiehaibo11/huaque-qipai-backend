package com.nanbei.entertainment.backend.room.application;

import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import java.util.UUID;

public record RoomPlacementView(
        boolean inRoom,
        String roomNumber,
        long lobbyId,
        long gameId,
        String gameRuleDisplay,
        int playerCount,
        int playCount,
        boolean owner) {
    public static RoomPlacementView none() {
        return new RoomPlacementView(false, "", 0, 0, "", 0, 0, false);
    }

    public static RoomPlacementView from(GameRoomEntity room, UUID userId) {
        return new RoomPlacementView(
                true,
                room.getRoomNumber(),
                room.getLobbyId(),
                room.getGameId(),
                room.getGameRuleDisplay(),
                room.getPlayerCount(),
                room.getPlayCount(),
                room.getOwnerUserId().equals(userId));
    }
}
