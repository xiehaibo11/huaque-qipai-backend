package com.nanbei.entertainment.backend.friend.infrastructure;

import com.nanbei.entertainment.backend.friend.domain.FriendNotificationType;
import java.time.Instant;
import java.util.UUID;

public record FriendNotificationRow(
        UUID id,
        FriendNotificationType type,
        long actorPublicPlayerId,
        String actorDisplayName,
        Instant createdAt) {}
