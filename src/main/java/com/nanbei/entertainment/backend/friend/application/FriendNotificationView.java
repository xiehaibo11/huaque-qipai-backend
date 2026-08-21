package com.nanbei.entertainment.backend.friend.application;

import com.nanbei.entertainment.backend.friend.domain.FriendNotificationType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FriendNotificationView(
        UUID id,
        FriendNotificationType type,
        Long actorPublicPlayerId,
        String actorDisplayName,
        Instant createdAt) {
    public record FriendNotificationList(
            long total, List<FriendNotificationView> notifications) {}
}
