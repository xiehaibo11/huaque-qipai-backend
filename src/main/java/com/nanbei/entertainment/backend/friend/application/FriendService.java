package com.nanbei.entertainment.backend.friend.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.friend.application.FriendApplicationView.FriendApplicationList;
import com.nanbei.entertainment.backend.friend.domain.FriendApplicationEntity;
import com.nanbei.entertainment.backend.friend.domain.FriendApplicationStatus;
import com.nanbei.entertainment.backend.friend.domain.FriendPresenceState;
import com.nanbei.entertainment.backend.friend.domain.FriendshipEntity;
import com.nanbei.entertainment.backend.friend.infrastructure.FriendApplicationRepository;
import com.nanbei.entertainment.backend.friend.infrastructure.FriendRow;
import com.nanbei.entertainment.backend.friend.infrastructure.FriendshipRepository;
import com.nanbei.entertainment.backend.gamehome.application.PlayerProfileService;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerProfileEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerProfileRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendService {
    static final int MAX_PAGE_SIZE = 50;

    private final FriendshipRepository friendshipRepository;
    private final FriendApplicationRepository applicationRepository;
    private final PlayerProfileRepository profileRepository;
    private final PlayerProfileService profileService;
    private final FriendPresenceService presenceService;

    public FriendService(
            FriendshipRepository friendshipRepository,
            FriendApplicationRepository applicationRepository,
            PlayerProfileRepository profileRepository,
            PlayerProfileService profileService,
            FriendPresenceService presenceService) {
        this.friendshipRepository = friendshipRepository;
        this.applicationRepository = applicationRepository;
        this.profileRepository = profileRepository;
        this.profileService = profileService;
        this.presenceService = presenceService;
    }

    @Transactional(readOnly = true)
    public FriendPage listFriends(UUID userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Slice<FriendRow> rows =
                friendshipRepository.findFriendRows(
                        userId,
                        presenceService.onlineSince(),
                        PageRequest.of(safePage, safeSize));
        List<FriendEntry> friends =
                rows.getContent().stream()
                        .map(row -> toFriendEntry(row))
                        .toList();
        return new FriendPage(safePage, safeSize, rows.hasNext(), friends);
    }

    private FriendEntry toFriendEntry(FriendRow row) {
        FriendPresenceState state =
                row.playerState() == null || row.playerState() <= 0
                        ? presenceService.stateOf(row.lastActiveAt())
                        : FriendPresenceState.fromPlayerState(
                                row.playerState());
        return new FriendEntry(
                row.publicPlayerId(),
                row.displayName(),
                row.avatarKey(),
                state,
                row.lastActiveAt(),
                row.shielded(),
                valueOrZero(row.chairCount()),
                valueOrZero(row.userCount()),
                parseRoomNumber(row.roomNumber()),
                row.gameId() == null ? 0L : row.gameId(),
                false);
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }

    private static int valueOrZero(Long value) {
        if (value == null || value <= 0L) {
            return 0;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : value.intValue();
    }

    private static int parseRoomNumber(String roomNumber) {
        if (roomNumber == null || roomNumber.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(roomNumber.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    @Transactional
    public void apply(UUID userId, long publicPlayerId) {
        UUID targetId = requireProfile(publicPlayerId).getUserId();
        if (targetId.equals(userId)) {
            throw new ApiException(
                    ErrorCode.FRIEND_SELF_OPERATION, "不能对自己执行该操作");
        }
        if (friendshipRepository.existsByUserIdAndFriendId(
                userId, targetId)) {
            throw new ApiException(
                    ErrorCode.FRIEND_ALREADY_FRIEND, "你们已经是好友");
        }
        if (applicationRepository.existsByRequesterIdAndTargetIdAndStatus(
                userId, targetId, FriendApplicationStatus.PENDING)) {
            throw new ApiException(
                    ErrorCode.FRIEND_APPLICATION_EXISTS, "已有待处理的好友申请");
        }
        profileService.ensureProfile(userId);
        applicationRepository.save(
                new FriendApplicationEntity(userId, targetId));
    }

    @Transactional(readOnly = true)
    public FriendApplicationList incomingApplications(UUID userId) {
        List<FriendApplicationView> applications =
                applicationRepository.findPendingRows(userId).stream()
                        .map(
                                row ->
                                        new FriendApplicationView(
                                                row.id(),
                                                row.publicPlayerId(),
                                                row.displayName(),
                                                row.avatarKey(),
                                                row.createdAt()))
                        .toList();
        return new FriendApplicationList(applications.size(), applications);
    }

    @Transactional
    public void accept(UUID userId, UUID applicationId) {
        FriendApplicationEntity application =
                requireApplicationForTarget(userId, applicationId);
        acceptApplication(userId, application);
    }

    @Transactional
    public void acceptByPublicPlayerId(UUID userId, long publicPlayerId) {
        FriendApplicationEntity application =
                requirePendingApplicationFrom(userId, publicPlayerId);
        acceptApplication(userId, application);
    }

    private void acceptApplication(
            UUID userId, FriendApplicationEntity application) {
        if (application.getStatus() == FriendApplicationStatus.PENDING) {
            application.accept(Instant.now());
        } else if (application.getStatus()
                != FriendApplicationStatus.ACCEPTED) {
            throw new ApiException(
                    ErrorCode.FRIEND_NOT_FOUND, "好友申请不存在或已处理");
        }
        profileService.ensureProfile(application.getRequesterId());
        profileService.ensureProfile(userId);
        link(application.getRequesterId(), userId);
        link(userId, application.getRequesterId());
    }

    @Transactional
    public void reject(UUID userId, UUID applicationId) {
        FriendApplicationEntity application =
                requireApplicationForTarget(userId, applicationId);
        rejectApplication(application);
    }

    @Transactional
    public void rejectByPublicPlayerId(UUID userId, long publicPlayerId) {
        FriendApplicationEntity application =
                requirePendingApplicationFrom(userId, publicPlayerId);
        rejectApplication(application);
    }

    private void rejectApplication(FriendApplicationEntity application) {
        if (application.getStatus() == FriendApplicationStatus.PENDING) {
            application.reject(Instant.now());
        } else if (application.getStatus()
                != FriendApplicationStatus.REJECTED) {
            throw new ApiException(
                    ErrorCode.FRIEND_NOT_FOUND, "好友申请不存在或已处理");
        }
    }

    private FriendApplicationEntity requirePendingApplicationFrom(
            UUID targetId, long requesterPublicPlayerId) {
        UUID requesterId = requireProfile(requesterPublicPlayerId).getUserId();
        return applicationRepository
                .findByRequesterIdAndTargetIdAndStatus(
                        requesterId,
                        targetId,
                        FriendApplicationStatus.PENDING)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        ErrorCode.FRIEND_NOT_FOUND,
                                        "好友申请不存在或已处理"));
    }

    @Transactional
    public void removeFriend(UUID userId, long publicPlayerId) {
        UUID targetId = requireProfile(publicPlayerId).getUserId();
        if (!friendshipRepository.existsByUserIdAndFriendId(
                userId, targetId)) {
            throw new ApiException(
                    ErrorCode.FRIEND_NOT_FRIEND, "对方不是你的好友");
        }
        friendshipRepository.deleteByUserIdAndFriendId(userId, targetId);
        friendshipRepository.deleteByUserIdAndFriendId(targetId, userId);
    }

    @Transactional
    public void setShielded(
            UUID userId, long publicPlayerId, boolean shielded) {
        UUID targetId = requireProfile(publicPlayerId).getUserId();
        FriendshipEntity friendship =
                friendshipRepository
                        .findByUserIdAndFriendId(userId, targetId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.FRIEND_NOT_FRIEND,
                                                "对方不是你的好友"));
        friendship.setShielded(shielded);
        friendshipRepository.save(friendship);
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

    private FriendApplicationEntity requireApplicationForTarget(
            UUID userId, UUID applicationId) {
        FriendApplicationEntity application =
                applicationRepository
                        .findById(applicationId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.FRIEND_NOT_FOUND,
                                                "好友申请不存在"));
        if (!application.getTargetId().equals(userId)) {
            throw new ApiException(
                    ErrorCode.FRIEND_NOT_FRIEND, "无权处理该好友申请");
        }
        return application;
    }

    private void link(UUID userId, UUID friendId) {
        if (!friendshipRepository.existsByUserIdAndFriendId(
                userId, friendId)) {
            friendshipRepository.save(new FriendshipEntity(userId, friendId));
        }
    }
}
