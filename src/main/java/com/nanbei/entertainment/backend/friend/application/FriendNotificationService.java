package com.nanbei.entertainment.backend.friend.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.friend.application.FriendNotificationView.FriendNotificationList;
import com.nanbei.entertainment.backend.friend.domain.FriendNotificationEntity;
import com.nanbei.entertainment.backend.friend.domain.FriendNotificationType;
import com.nanbei.entertainment.backend.friend.infrastructure.FriendNotificationRepository;
import com.nanbei.entertainment.backend.friend.infrastructure.FriendshipRepository;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerProfileEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerProfileRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendNotificationService {
    private static final Duration INVITE_COOLDOWN = Duration.ofSeconds(30);
    private static final Duration RECALL_COOLDOWN = Duration.ofHours(24);

    private final FriendshipRepository friendshipRepository;
    private final FriendNotificationRepository notificationRepository;
    private final PlayerProfileRepository profileRepository;
    private final FriendPresenceService presenceService;

    public FriendNotificationService(
            FriendshipRepository friendshipRepository,
            FriendNotificationRepository notificationRepository,
            PlayerProfileRepository profileRepository,
            FriendPresenceService presenceService) {
        this.friendshipRepository = friendshipRepository;
        this.notificationRepository = notificationRepository;
        this.profileRepository = profileRepository;
        this.presenceService = presenceService;
    }

    @Transactional
    public void invite(
            UUID userId, long publicPlayerId, FriendNotificationType type) {
        UUID targetId = requireProfile(publicPlayerId).getUserId();
        if (!friendshipRepository.existsByUserIdAndFriendId(
                userId, targetId)) {
            throw new ApiException(
                    ErrorCode.FRIEND_NOT_FRIEND, "对方不是你的好友");
        }
        Duration cooldown =
                type == FriendNotificationType.RECALL
                        ? RECALL_COOLDOWN
                        : INVITE_COOLDOWN;
        boolean coolingDown =
                notificationRepository
                        .existsByUserIdAndActorIdAndTypeAndCreatedAtAfter(
                                targetId,
                                userId,
                                type,
                                Instant.now().minus(cooldown));
        if (coolingDown) {
            if (type == FriendNotificationType.RECALL) {
                throw new ApiException(
                        ErrorCode.FRIEND_RECALL_TOO_FREQUENT,
                        "召回过于频繁，请24小时后再试");
            }
            throw new ApiException(
                    ErrorCode.FRIEND_INVITE_TOO_FREQUENT,
                    "邀请过于频繁，请稍后再试");
        }
        notificationRepository.save(
                new FriendNotificationEntity(targetId, userId, type));
    }

    @Transactional
    public FriendInviteAllResult inviteAll(UUID userId) {
        List<UUID> onlineFriendIds =
                friendshipRepository.findOnlineUnshieldedFriendIds(
                        userId, presenceService.onlineSince());
        if (onlineFriendIds.isEmpty()) {
            return new FriendInviteAllResult(0, 0);
        }
        Set<UUID> coolingDown =
                notificationRepository.findUserIdsWithNotificationAfter(
                        userId,
                        FriendNotificationType.INVITE,
                        Instant.now().minus(INVITE_COOLDOWN),
                        onlineFriendIds);
        List<FriendNotificationEntity> notifications =
                onlineFriendIds.stream()
                        .filter(friendId -> !coolingDown.contains(friendId))
                        .map(
                                friendId ->
                                        new FriendNotificationEntity(
                                                friendId,
                                                userId,
                                                FriendNotificationType
                                                        .INVITE))
                        .toList();
        notificationRepository.saveAll(notifications);
        return new FriendInviteAllResult(
                notifications.size(), coolingDown.size());
    }

    @Transactional(readOnly = true)
    public FriendNotificationList notifications(
            UUID userId, boolean unreadOnly) {
        List<FriendNotificationView> notifications =
                notificationRepository.findRows(userId, unreadOnly).stream()
                        .map(
                                row ->
                                        new FriendNotificationView(
                                                row.id(),
                                                row.type(),
                                                row.actorPublicPlayerId(),
                                                row.actorDisplayName(),
                                                row.createdAt()))
                        .toList();
        return new FriendNotificationList(
                notifications.size(), notifications);
    }

    @Transactional
    public void markAllNotificationsRead(UUID userId) {
        notificationRepository.markAllRead(userId, Instant.now());
    }

    private PlayerProfileEntity requireProfile(long publicPlayerId) {
        return profileRepository
                .findByPublicPlayerId(publicPlayerId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        ErrorCode.FRIEND_NOT_FOUND,
                                        "未找到该玩家"));
    }
}
