package com.nanbei.entertainment.backend.friend.application;

import static com.nanbei.entertainment.backend.friend.application.FriendServiceTest.profileOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.friend.application.FriendNotificationView.FriendNotificationList;
import com.nanbei.entertainment.backend.friend.domain.FriendNotificationEntity;
import com.nanbei.entertainment.backend.friend.domain.FriendNotificationType;
import com.nanbei.entertainment.backend.friend.infrastructure.FriendNotificationRepository;
import com.nanbei.entertainment.backend.friend.infrastructure.FriendNotificationRow;
import com.nanbei.entertainment.backend.friend.infrastructure.FriendshipRepository;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerProfileRepository;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FriendNotificationServiceTest {
    @Mock FriendshipRepository friendshipRepository;
    @Mock FriendNotificationRepository notificationRepository;
    @Mock PlayerProfileRepository profileRepository;
    @Mock FriendPresenceService presenceService;

    FriendNotificationService service;

    @BeforeEach
    void setUp() {
        service =
                new FriendNotificationService(
                        friendshipRepository,
                        notificationRepository,
                        profileRepository,
                        presenceService);
    }

    @Test
    void inviteWritesNotificationForFriend() {
        UUID userId = UUID.randomUUID();
        UserEntity target = UserEntity.create("手机用户0020");
        when(profileRepository.findByPublicPlayerId(1084375590L))
                .thenReturn(Optional.of(profileOf(target, 1084375590L)));
        when(friendshipRepository.existsByUserIdAndFriendId(
                        userId, target.getId()))
                .thenReturn(true);

        service.invite(
                userId, 1084375590L, FriendNotificationType.INVITE);

        ArgumentCaptor<FriendNotificationEntity> captor =
                ArgumentCaptor.forClass(FriendNotificationEntity.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId())
                .isEqualTo(target.getId());
        assertThat(captor.getValue().getActorId()).isEqualTo(userId);
        assertThat(captor.getValue().getType())
                .isEqualTo(FriendNotificationType.INVITE);
    }

    @Test
    void rejectsInviteToStranger() {
        UUID userId = UUID.randomUUID();
        UserEntity target = UserEntity.create("手机用户0021");
        when(profileRepository.findByPublicPlayerId(1084375590L))
                .thenReturn(Optional.of(profileOf(target, 1084375590L)));

        assertThatThrownBy(
                        () ->
                                service.invite(
                                        userId,
                                        1084375590L,
                                        FriendNotificationType.RESERVE))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FRIEND_NOT_FRIEND);
    }

    @Test
    void rejectsInviteInsideCooldownWindow() {
        UUID userId = UUID.randomUUID();
        UserEntity target = UserEntity.create("手机用户0022");
        when(profileRepository.findByPublicPlayerId(1084375590L))
                .thenReturn(Optional.of(profileOf(target, 1084375590L)));
        when(friendshipRepository.existsByUserIdAndFriendId(
                        userId, target.getId()))
                .thenReturn(true);
        when(notificationRepository
                        .existsByUserIdAndActorIdAndTypeAndCreatedAtAfter(
                                eq(target.getId()),
                                eq(userId),
                                eq(FriendNotificationType.INVITE),
                                any()))
                .thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.invite(
                                        userId,
                                        1084375590L,
                                        FriendNotificationType.INVITE))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FRIEND_INVITE_TOO_FREQUENT);
    }

    @Test
    void listsUnreadNotifications() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-31T08:00:00Z");
        when(notificationRepository.findRows(userId, true))
                .thenReturn(
                        List.of(
                                new FriendNotificationRow(
                                        notificationId,
                                        FriendNotificationType.RESERVE,
                                        1084375590L,
                                        "牌友甲",
                                        createdAt)));

        FriendNotificationList list = service.notifications(userId, true);

        assertThat(list.total()).isEqualTo(1);
        assertThat(list.notifications().get(0).type())
                .isEqualTo(FriendNotificationType.RESERVE);
        assertThat(list.notifications().get(0).actorPublicPlayerId())
                .isEqualTo(1084375590L);
    }

    @Test
    void marksAllNotificationsRead() {
        UUID userId = UUID.randomUUID();

        service.markAllNotificationsRead(userId);

        verify(notificationRepository).markAllRead(eq(userId), any());
    }

    @Test
    void recallWritesNotificationForFriend() {
        UUID userId = UUID.randomUUID();
        UserEntity target = UserEntity.create("手机用户0023");
        when(profileRepository.findByPublicPlayerId(1084375590L))
                .thenReturn(Optional.of(profileOf(target, 1084375590L)));
        when(friendshipRepository.existsByUserIdAndFriendId(
                        userId, target.getId()))
                .thenReturn(true);

        service.invite(
                userId, 1084375590L, FriendNotificationType.RECALL);

        ArgumentCaptor<FriendNotificationEntity> captor =
                ArgumentCaptor.forClass(FriendNotificationEntity.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType())
                .isEqualTo(FriendNotificationType.RECALL);
    }

    @Test
    void rejectsRecallInsideCooldownWindowWithRecallError() {
        UUID userId = UUID.randomUUID();
        UserEntity target = UserEntity.create("手机用户0024");
        when(profileRepository.findByPublicPlayerId(1084375590L))
                .thenReturn(Optional.of(profileOf(target, 1084375590L)));
        when(friendshipRepository.existsByUserIdAndFriendId(
                        userId, target.getId()))
                .thenReturn(true);
        when(notificationRepository
                        .existsByUserIdAndActorIdAndTypeAndCreatedAtAfter(
                                eq(target.getId()),
                                eq(userId),
                                eq(FriendNotificationType.RECALL),
                                any()))
                .thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.invite(
                                        userId,
                                        1084375590L,
                                        FriendNotificationType.RECALL))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FRIEND_RECALL_TOO_FREQUENT);
    }

    @Test
    void inviteCooldownOnlyConsidersMatchingNotificationType() {
        UUID userId = UUID.randomUUID();
        UserEntity target = UserEntity.create("手机用户0025");
        when(profileRepository.findByPublicPlayerId(1084375590L))
                .thenReturn(Optional.of(profileOf(target, 1084375590L)));
        when(friendshipRepository.existsByUserIdAndFriendId(
                        userId, target.getId()))
                .thenReturn(true);

        service.invite(userId, 1084375590L, FriendNotificationType.RESERVE);

        verify(notificationRepository)
                .existsByUserIdAndActorIdAndTypeAndCreatedAtAfter(
                        eq(target.getId()),
                        eq(userId),
                        eq(FriendNotificationType.RESERVE),
                        any());
    }

    @Test
    void inviteAllWritesInvitesForOnlineFriendsOutsideCooldown() {
        UUID userId = UUID.randomUUID();
        UUID friendA = UUID.randomUUID();
        UUID friendB = UUID.randomUUID();
        UUID friendC = UUID.randomUUID();
        List<UUID> onlineFriendIds = List.of(friendA, friendB, friendC);
        Instant onlineSince = Instant.parse("2026-07-31T08:00:00Z");
        when(presenceService.onlineSince()).thenReturn(onlineSince);
        when(friendshipRepository.findOnlineUnshieldedFriendIds(
                        userId, onlineSince))
                .thenReturn(onlineFriendIds);
        when(notificationRepository.findUserIdsWithNotificationAfter(
                        eq(userId),
                        eq(FriendNotificationType.INVITE),
                        any(),
                        eq(onlineFriendIds)))
                .thenReturn(Set.of(friendB));

        FriendInviteAllResult result = service.inviteAll(userId);

        assertThat(result.invitedCount()).isEqualTo(2);
        assertThat(result.cooldownSkippedCount()).isEqualTo(1);
        ArgumentCaptor<List<FriendNotificationEntity>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(FriendNotificationEntity::getUserId)
                .containsExactlyInAnyOrder(friendA, friendC);
        assertThat(captor.getValue())
                .allMatch(
                        notification ->
                                notification.getType()
                                                == FriendNotificationType
                                                        .INVITE
                                        && notification
                                                .getActorId()
                                                .equals(userId));
    }

    @Test
    void inviteAllWithoutOnlineFriendsSkipsPersistence() {
        UUID userId = UUID.randomUUID();
        Instant onlineSince = Instant.parse("2026-07-31T08:00:00Z");
        when(presenceService.onlineSince()).thenReturn(onlineSince);
        when(friendshipRepository.findOnlineUnshieldedFriendIds(
                        userId, onlineSince))
                .thenReturn(List.of());

        FriendInviteAllResult result = service.inviteAll(userId);

        assertThat(result.invitedCount()).isZero();
        assertThat(result.cooldownSkippedCount()).isZero();
        verify(notificationRepository, never()).saveAll(any());
    }
}
