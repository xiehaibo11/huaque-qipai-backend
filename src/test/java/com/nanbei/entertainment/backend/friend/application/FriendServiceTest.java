package com.nanbei.entertainment.backend.friend.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.friend.application.FriendApplicationView.FriendApplicationList;
import com.nanbei.entertainment.backend.friend.domain.FriendApplicationEntity;
import com.nanbei.entertainment.backend.friend.domain.FriendApplicationStatus;
import com.nanbei.entertainment.backend.friend.domain.FriendPresenceState;
import com.nanbei.entertainment.backend.friend.domain.FriendshipEntity;
import com.nanbei.entertainment.backend.friend.infrastructure.FriendApplicationRepository;
import com.nanbei.entertainment.backend.friend.infrastructure.FriendApplicationRow;
import com.nanbei.entertainment.backend.friend.infrastructure.FriendRow;
import com.nanbei.entertainment.backend.friend.infrastructure.FriendshipRepository;
import com.nanbei.entertainment.backend.gamehome.application.PlayerProfileService;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerProfileEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerProfileRepository;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Slice;

@ExtendWith(MockitoExtension.class)
class FriendServiceTest {
    @Mock FriendshipRepository friendshipRepository;
    @Mock FriendApplicationRepository applicationRepository;
    @Mock PlayerProfileRepository profileRepository;
    @Mock PlayerProfileService profileService;
    @Mock FriendPresenceService presenceService;

    FriendService service;

    @BeforeEach
    void setUp() {
        service =
                new FriendService(
                        friendshipRepository,
                        applicationRepository,
                        profileRepository,
                        profileService,
                        presenceService);
    }

    @Test
    void listsFriendsWithPresenceAndShieldState() {
        UUID userId = UUID.randomUUID();
        Instant lastActiveAt = Instant.parse("2026-07-31T10:00:00Z");
        FriendRow row =
                new FriendRow(
                        1084375590L, "牌友甲", "avatar_default",
                        lastActiveAt, true);
        Slice<FriendRow> slice = sliceOf(List.of(row));
        when(presenceService.onlineSince())
                .thenReturn(Instant.parse("2026-07-31T10:05:00Z"));
        when(friendshipRepository.findFriendRows(eq(userId), any(), any()))
                .thenReturn(slice);
        when(presenceService.stateOf(lastActiveAt))
                .thenReturn(FriendPresenceState.ONLINE);

        FriendPage page = service.listFriends(userId, 0, 20);

        assertThat(page.hasMore()).isFalse();
        assertThat(page.friends()).hasSize(1);
        FriendEntry entry = page.friends().get(0);
        assertThat(entry.publicPlayerId()).isEqualTo(1084375590L);
        assertThat(entry.displayName()).isEqualTo("牌友甲");
        assertThat(entry.state()).isEqualTo(FriendPresenceState.ONLINE);
        assertThat(entry.lastActiveAt()).isEqualTo(lastActiveAt);
        assertThat(entry.shielded()).isTrue();
    }

    @Test
    void listsFriendsWithOriginalWaitingRoomPresence() {
        UUID userId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();
        Instant lastActiveAt = Instant.parse("2026-07-31T10:00:00Z");
        FriendRow row =
                new FriendRow(
                        friendId,
                        1084375591L,
                        "牌友等开局",
                        "avatar_wait",
                        lastActiveAt,
                        false,
                        8,
                        4,
                        3L,
                        "123456",
                        30588L);
        Slice<FriendRow> slice = sliceOf(List.of(row));
        when(presenceService.onlineSince())
                .thenReturn(Instant.parse("2026-07-31T10:05:00Z"));
        when(friendshipRepository.findFriendRows(eq(userId), any(), any()))
                .thenReturn(slice);

        FriendPage page = service.listFriends(userId, 0, 20);

        FriendEntry entry = page.friends().get(0);
        assertThat(entry.state()).isEqualTo(FriendPresenceState.WAITING);
        assertThat(entry.playerState()).isEqualTo(8);
        assertThat(entry.chairCount()).isEqualTo(4);
        assertThat(entry.userCount()).isEqualTo(3);
        assertThat(entry.roomId()).isEqualTo(123456);
        assertThat(entry.gameId()).isEqualTo(30588L);
    }

    @Test
    void capsFriendListPageSize() {
        UUID userId = UUID.randomUUID();
        Slice<FriendRow> slice = sliceOf(List.of());
        when(presenceService.onlineSince()).thenReturn(Instant.now());
        when(friendshipRepository.findFriendRows(eq(userId), any(), any()))
                .thenReturn(slice);

        FriendPage page = service.listFriends(userId, 0, 200);

        assertThat(page.size()).isEqualTo(FriendService.MAX_PAGE_SIZE);
    }

    @Test
    void appliesForFriendshipAndEnsuresRequesterProfile() {
        UUID requesterId = UUID.randomUUID();
        UserEntity target = UserEntity.create("手机用户0001");
        when(profileRepository.findByPublicPlayerId(1084375590L))
                .thenReturn(Optional.of(profileOf(target, 1084375590L)));

        service.apply(requesterId, 1084375590L);

        ArgumentCaptor<FriendApplicationEntity> captor =
                ArgumentCaptor.forClass(FriendApplicationEntity.class);
        verify(applicationRepository).save(captor.capture());
        assertThat(captor.getValue().getRequesterId())
                .isEqualTo(requesterId);
        assertThat(captor.getValue().getTargetId())
                .isEqualTo(target.getId());
        assertThat(captor.getValue().getStatus())
                .isEqualTo(FriendApplicationStatus.PENDING);
        verify(profileService).ensureProfile(requesterId);
    }

    @Test
    void rejectsApplicationToUnknownPlayer() {
        when(profileRepository.findByPublicPlayerId(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.apply(UUID.randomUUID(), 1L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FRIEND_NOT_FOUND);
    }

    @Test
    void rejectsApplicationToSelf() {
        UserEntity self = UserEntity.create("手机用户0002");
        when(profileRepository.findByPublicPlayerId(1084375590L))
                .thenReturn(Optional.of(profileOf(self, 1084375590L)));

        assertThatThrownBy(
                        () -> service.apply(self.getId(), 1084375590L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FRIEND_SELF_OPERATION);
    }

    @Test
    void rejectsApplicationWhenAlreadyFriends() {
        UUID requesterId = UUID.randomUUID();
        UserEntity target = UserEntity.create("手机用户0003");
        when(profileRepository.findByPublicPlayerId(1084375590L))
                .thenReturn(Optional.of(profileOf(target, 1084375590L)));
        when(friendshipRepository.existsByUserIdAndFriendId(
                        requesterId, target.getId()))
                .thenReturn(true);

        assertThatThrownBy(
                        () -> service.apply(requesterId, 1084375590L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FRIEND_ALREADY_FRIEND);
    }

    @Test
    void rejectsDuplicatePendingApplication() {
        UUID requesterId = UUID.randomUUID();
        UserEntity target = UserEntity.create("手机用户0004");
        when(profileRepository.findByPublicPlayerId(1084375590L))
                .thenReturn(Optional.of(profileOf(target, 1084375590L)));
        when(applicationRepository
                        .existsByRequesterIdAndTargetIdAndStatus(
                                requesterId,
                                target.getId(),
                                FriendApplicationStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(
                        () -> service.apply(requesterId, 1084375590L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FRIEND_APPLICATION_EXISTS);
    }

    @Test
    void listsIncomingPendingApplications() {
        UUID targetId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-31T09:00:00Z");
        UUID applicationId = UUID.randomUUID();
        when(applicationRepository.findPendingRows(targetId))
                .thenReturn(
                        List.of(
                                new FriendApplicationRow(
                                        applicationId,
                                        1084375590L,
                                        "牌友甲",
                                        "avatar_default",
                                        createdAt)));

        FriendApplicationList list = service.incomingApplications(targetId);

        assertThat(list.total()).isEqualTo(1);
        assertThat(list.applications().get(0).id())
                .isEqualTo(applicationId);
        assertThat(list.applications().get(0).publicPlayerId())
                .isEqualTo(1084375590L);
    }

    @Test
    void acceptLinksBothDirections() {
        UserEntity requester = UserEntity.create("手机用户0005");
        UserEntity target = UserEntity.create("手机用户0006");
        FriendApplicationEntity application =
                new FriendApplicationEntity(requester.getId(), target.getId());
        when(applicationRepository.findById(application.getId()))
                .thenReturn(Optional.of(application));

        service.accept(target.getId(), application.getId());

        assertThat(application.getStatus())
                .isEqualTo(FriendApplicationStatus.ACCEPTED);
        assertThat(application.getHandledAt()).isNotNull();
        ArgumentCaptor<FriendshipEntity> captor =
                ArgumentCaptor.forClass(FriendshipEntity.class);
        verify(friendshipRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .anySatisfy(
                        friendship -> {
                            assertThat(friendship.getUserId())
                                    .isEqualTo(requester.getId());
                            assertThat(friendship.getFriendId())
                                    .isEqualTo(target.getId());
                        })
                .anySatisfy(
                        friendship -> {
                            assertThat(friendship.getUserId())
                                    .isEqualTo(target.getId());
                            assertThat(friendship.getFriendId())
                                    .isEqualTo(requester.getId());
                        });
        verify(profileService).ensureProfile(requester.getId());
        verify(profileService).ensureProfile(target.getId());
    }

    @Test
    void acceptsByRequesterPublicPlayerIdLikeOriginalNumid() {
        UserEntity requester = UserEntity.create("手机用户0105");
        UserEntity target = UserEntity.create("手机用户0106");
        FriendApplicationEntity application =
                new FriendApplicationEntity(requester.getId(), target.getId());
        when(profileRepository.findByPublicPlayerId(1084375590L))
                .thenReturn(Optional.of(profileOf(requester, 1084375590L)));
        when(applicationRepository
                        .findByRequesterIdAndTargetIdAndStatus(
                                requester.getId(),
                                target.getId(),
                                FriendApplicationStatus.PENDING))
                .thenReturn(Optional.of(application));

        service.acceptByPublicPlayerId(target.getId(), 1084375590L);

        assertThat(application.getStatus())
                .isEqualTo(FriendApplicationStatus.ACCEPTED);
        verify(friendshipRepository, times(2))
                .save(any(FriendshipEntity.class));
    }

    @Test
    void acceptIsIdempotentWhenFriendshipAlreadyExists() {
        UserEntity requester = UserEntity.create("手机用户0007");
        UserEntity target = UserEntity.create("手机用户0008");
        FriendApplicationEntity application =
                new FriendApplicationEntity(requester.getId(), target.getId());
        application.accept(Instant.now());
        when(applicationRepository.findById(application.getId()))
                .thenReturn(Optional.of(application));
        when(friendshipRepository.existsByUserIdAndFriendId(any(), any()))
                .thenReturn(true);

        service.accept(target.getId(), application.getId());

        verify(friendshipRepository, never())
                .save(any(FriendshipEntity.class));
    }

    @Test
    void rejectsAcceptFromNonTargetUser() {
        UserEntity requester = UserEntity.create("手机用户0009");
        UserEntity target = UserEntity.create("手机用户0010");
        FriendApplicationEntity application =
                new FriendApplicationEntity(requester.getId(), target.getId());
        when(applicationRepository.findById(application.getId()))
                .thenReturn(Optional.of(application));

        assertThatThrownBy(
                        () ->
                                service.accept(
                                        requester.getId(),
                                        application.getId()))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FRIEND_NOT_FRIEND);
    }

    @Test
    void rejectMarksApplicationRejected() {
        UserEntity requester = UserEntity.create("手机用户0011");
        UserEntity target = UserEntity.create("手机用户0012");
        FriendApplicationEntity application =
                new FriendApplicationEntity(requester.getId(), target.getId());
        when(applicationRepository.findById(application.getId()))
                .thenReturn(Optional.of(application));

        service.reject(target.getId(), application.getId());

        assertThat(application.getStatus())
                .isEqualTo(FriendApplicationStatus.REJECTED);
        verify(friendshipRepository, never())
                .save(any(FriendshipEntity.class));
    }

    @Test
    void rejectsByRequesterPublicPlayerIdLikeOriginalNumid() {
        UserEntity requester = UserEntity.create("手机用户0111");
        UserEntity target = UserEntity.create("手机用户0112");
        FriendApplicationEntity application =
                new FriendApplicationEntity(requester.getId(), target.getId());
        when(profileRepository.findByPublicPlayerId(1084375590L))
                .thenReturn(Optional.of(profileOf(requester, 1084375590L)));
        when(applicationRepository
                        .findByRequesterIdAndTargetIdAndStatus(
                                requester.getId(),
                                target.getId(),
                                FriendApplicationStatus.PENDING))
                .thenReturn(Optional.of(application));

        service.rejectByPublicPlayerId(target.getId(), 1084375590L);

        assertThat(application.getStatus())
                .isEqualTo(FriendApplicationStatus.REJECTED);
        verify(friendshipRepository, never())
                .save(any(FriendshipEntity.class));
    }

    @Test
    void removesFriendshipInBothDirections() {
        UUID userId = UUID.randomUUID();
        UserEntity target = UserEntity.create("手机用户0013");
        when(profileRepository.findByPublicPlayerId(1084375590L))
                .thenReturn(Optional.of(profileOf(target, 1084375590L)));
        when(friendshipRepository.existsByUserIdAndFriendId(
                        userId, target.getId()))
                .thenReturn(true);

        service.removeFriend(userId, 1084375590L);

        verify(friendshipRepository)
                .deleteByUserIdAndFriendId(userId, target.getId());
        verify(friendshipRepository)
                .deleteByUserIdAndFriendId(target.getId(), userId);
    }

    @Test
    void rejectsRemovingStranger() {
        UUID userId = UUID.randomUUID();
        UserEntity target = UserEntity.create("手机用户0014");
        when(profileRepository.findByPublicPlayerId(1084375590L))
                .thenReturn(Optional.of(profileOf(target, 1084375590L)));

        assertThatThrownBy(
                        () -> service.removeFriend(userId, 1084375590L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FRIEND_NOT_FRIEND);
    }

    @Test
    void shieldOnlyUpdatesOwnDirectionRow() {
        UUID userId = UUID.randomUUID();
        UserEntity target = UserEntity.create("手机用户0015");
        FriendshipEntity ownRow =
                new FriendshipEntity(userId, target.getId());
        when(profileRepository.findByPublicPlayerId(1084375590L))
                .thenReturn(Optional.of(profileOf(target, 1084375590L)));
        when(friendshipRepository.findByUserIdAndFriendId(
                        userId, target.getId()))
                .thenReturn(Optional.of(ownRow));

        service.setShielded(userId, 1084375590L, true);

        assertThat(ownRow.isShielded()).isTrue();
        verify(friendshipRepository).save(ownRow);
    }

    static PlayerProfileEntity profileOf(
            UserEntity user, long publicPlayerId) {
        return new PlayerProfileEntity(
                user.getId(), publicPlayerId, "avatar_default", 0);
    }

    @SuppressWarnings("unchecked")
    private static Slice<FriendRow> sliceOf(
            List<FriendRow> content) {
        Slice<FriendRow> slice =
                Mockito.mock(Slice.class);
        Mockito.when(slice.getContent()).thenReturn(content);
        Mockito.when(slice.hasNext()).thenReturn(false);
        return slice;
    }
}
