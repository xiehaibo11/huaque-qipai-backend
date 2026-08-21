package com.nanbei.entertainment.backend.friend.infrastructure;

import java.time.Instant;
import java.util.UUID;

public record FriendApplicationRow(
        UUID id,
        long publicPlayerId,
        String displayName,
        String avatarKey,
        Instant createdAt) {}
