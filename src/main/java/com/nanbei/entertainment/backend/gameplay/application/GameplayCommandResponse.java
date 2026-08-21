package com.nanbei.entertainment.backend.gameplay.application;

public record GameplayCommandResponse(
        long revision,
        String eventType,
        int seatNumber,
        boolean ready,
        boolean replayed) {
    GameplayCommandResponse asReplay() {
        return new GameplayCommandResponse(revision, eventType, seatNumber, ready, true);
    }
}
