package com.nanbei.entertainment.backend.gameplay.application;

import java.util.UUID;

public record GameplaySeatSnapshot(
        int seatNumber,
        UUID userId,
        long publicPlayerId,
        String displayName,
        String avatarKey,
        long score,
        boolean host,
        boolean ready,
        boolean connected) {
    public GameplaySeatSnapshot {
        if (seatNumber <= 0 || publicPlayerId <= 0) {
            throw new IllegalArgumentException("invalid gameplay seat identity");
        }
        if (userId == null
                || displayName == null
                || displayName.isBlank()
                || avatarKey == null
                || avatarKey.isBlank()) {
            throw new IllegalArgumentException("incomplete gameplay seat profile");
        }
    }
}
