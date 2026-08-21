package com.nanbei.entertainment.backend.room.application;

import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import com.nanbei.entertainment.backend.room.domain.RoomStatus;
import java.time.Instant;

public record RoomSnapshot(
        String roomNumber,
        RoomStatus status,
        long lobbyId,
        long gameId,
        String gameRule,
        String gameRuleDisplay,
        String roomRule,
        int roomMode,
        int playerCount,
        int playCount,
        RoomPayType payType,
        int roomFeeCenti,
        Instant createdAt,
        Instant firstRoundAt,
        Instant closedAt) {
    static RoomSnapshot from(GameRoomEntity room) {
        return new RoomSnapshot(
                room.getRoomNumber(),
                room.getStatus(),
                room.getLobbyId(),
                room.getGameId(),
                room.getGameRule(),
                room.getGameRuleDisplay(),
                room.getRoomRule(),
                room.getRoomMode(),
                room.getPlayerCount(),
                room.getPlayCount(),
                room.getPayType(),
                room.getRoomFeeCenti(),
                room.getCreatedAt(),
                room.getFirstRoundAt(),
                room.getClosedAt());
    }
}
