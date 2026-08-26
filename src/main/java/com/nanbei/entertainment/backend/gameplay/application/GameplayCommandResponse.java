package com.nanbei.entertainment.backend.gameplay.application;

import java.util.List;

public record GameplayCommandResponse(
        long revision,
        String eventType,
        int seatNumber,
        boolean ready,
        boolean replayed,
        List<GameplayEventView> events) {
    public GameplayCommandResponse {
        events = events == null ? List.of() : List.copyOf(events);
    }

    public GameplayCommandResponse(
            long revision,
            String eventType,
            int seatNumber,
            boolean ready,
            boolean replayed) {
        this(revision, eventType, seatNumber, ready, replayed, List.of());
    }

    GameplayCommandResponse asReplay() {
        return new GameplayCommandResponse(revision, eventType, seatNumber, ready, true, events);
    }
}
