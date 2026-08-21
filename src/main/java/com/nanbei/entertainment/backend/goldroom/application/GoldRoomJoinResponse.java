package com.nanbei.entertainment.backend.goldroom.application;

/** First-party response that mirrors the original 50-mode dispatch-queue handoff. */
public record GoldRoomJoinResponse(
        String code,
        String status,
        String roomMode,
        long lobbyId,
        long gameId,
        long boxGameId,
        int roomNameFlag,
        int sessionId,
        int chairCount,
        long baseScore,
        boolean dynamicCost,
        long minRich,
        long maxRich,
        String matchingTicketId,
        String message,
        String roomNumber,
        boolean autoGameplay,
        boolean replay) {
    public GoldRoomJoinResponse asReplay() {
        return new GoldRoomJoinResponse(
                code,
                status,
                roomMode,
                lobbyId,
                gameId,
                boxGameId,
                roomNameFlag,
                sessionId,
                chairCount,
                baseScore,
                dynamicCost,
                minRich,
                maxRich,
                matchingTicketId,
                message,
                roomNumber,
                autoGameplay,
                true);
    }
}
