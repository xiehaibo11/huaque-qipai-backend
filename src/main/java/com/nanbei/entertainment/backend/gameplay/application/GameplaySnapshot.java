package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.gameplay.domain.GamePhase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record GameplaySnapshot(
        UUID sessionId,
        String roomNumber,
        long gameId,
        GamePhase phase,
        int roundNumber,
        long revision,
        int chairCount,
        int maxPlayCount,
        String gameRuleDisplay,
        boolean autoReady,
        int mySeat,
        List<GameplaySeatSnapshot> seats,
        JsonNode visibleRound,
        JsonNode playPermission,
        JsonNode settlement,
        JsonNode multipleChoice,
        Integer activeSeat,
        Integer clockRemainingSeconds,
        int remainingWallCount,
        JsonNode actionOffer,
        JsonNode melds,
        JsonNode flowers,
        JsonNode tingInfo,
        Integer shengPaiCount,
        Integer leftBankerCount,
        Instant updatedAt) {
    public GameplaySnapshot(
            UUID sessionId,
            String roomNumber,
            long gameId,
            GamePhase phase,
            int roundNumber,
            long revision,
            int chairCount,
            int maxPlayCount,
            String gameRuleDisplay,
            boolean autoReady,
            int mySeat,
            List<GameplaySeatSnapshot> seats,
            JsonNode visibleRound,
            JsonNode playPermission,
            JsonNode settlement,
            Instant updatedAt) {
        this(
                sessionId,
                roomNumber,
                gameId,
                phase,
                roundNumber,
                revision,
                chairCount,
                maxPlayCount,
                gameRuleDisplay,
                autoReady,
                mySeat,
                seats,
                visibleRound,
                playPermission,
                settlement,
                null,
                null,
                null,
                -1,
                null,
                null,
                null,
                null,
                null,
                null,
                updatedAt);
    }

    public GameplaySnapshot(
            UUID sessionId,
            String roomNumber,
            long gameId,
            GamePhase phase,
            int roundNumber,
            long revision,
            int chairCount,
            int maxPlayCount,
            String gameRuleDisplay,
            boolean autoReady,
            int mySeat,
            List<GameplaySeatSnapshot> seats,
            Instant updatedAt) {
        this(
                sessionId,
                roomNumber,
                gameId,
                phase,
                roundNumber,
                revision,
                chairCount,
                maxPlayCount,
                gameRuleDisplay,
                autoReady,
                mySeat,
                seats,
                null,
                null,
                null,
                null,
                null,
                null,
                -1,
                null,
                null,
                null,
                null,
                null,
                null,
                updatedAt);
    }

    public GameplaySnapshot {
        seats = List.copyOf(seats);
        if (activeSeat != null && (activeSeat <= 0 || activeSeat > chairCount)) {
            throw new IllegalArgumentException("activeSeat is outside chairCount");
        }
        if (clockRemainingSeconds != null && clockRemainingSeconds < 0) {
            throw new IllegalArgumentException("clockRemainingSeconds must be non-negative");
        }
        if (remainingWallCount < -1) {
            throw new IllegalArgumentException("remainingWallCount must be -1 or non-negative");
        }
    }
}
