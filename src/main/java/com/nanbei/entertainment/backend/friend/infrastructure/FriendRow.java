package com.nanbei.entertainment.backend.friend.infrastructure;

import java.time.Instant;
import java.util.UUID;

public record FriendRow(
        UUID friendUserId,
        long publicPlayerId,
        String displayName,
        String avatarKey,
        Instant lastActiveAt,
        boolean shielded,
        Integer playerState,
        Integer chairCount,
        Long userCount,
        String roomNumber,
        Long gameId) {
    public FriendRow(
            long publicPlayerId,
            String displayName,
            String avatarKey,
            Instant lastActiveAt,
            boolean shielded) {
        this(
                null,
                publicPlayerId,
                displayName,
                avatarKey,
                lastActiveAt,
                shielded,
                null,
                null,
                null,
                null,
                null);
    }
}
