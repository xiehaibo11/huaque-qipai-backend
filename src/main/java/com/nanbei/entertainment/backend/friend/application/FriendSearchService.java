package com.nanbei.entertainment.backend.friend.application;

import com.nanbei.entertainment.backend.auth.application.MainlandPhoneNumber;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.friend.domain.FriendApplicationStatus;
import com.nanbei.entertainment.backend.friend.domain.FriendRelation;
import com.nanbei.entertainment.backend.friend.infrastructure.FriendApplicationRepository;
import com.nanbei.entertainment.backend.friend.infrastructure.FriendshipRepository;
import com.nanbei.entertainment.backend.gamehome.application.PlayerProfileService;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerProfileEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerProfileRepository;
import com.nanbei.entertainment.backend.user.domain.IdentityProvider;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import com.nanbei.entertainment.backend.user.domain.UserIdentityEntity;
import com.nanbei.entertainment.backend.user.infrastructure.UserIdentityRepository;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendSearchService {
    private static final String PUBLIC_ID_PATTERN = "\\d{1,10}";
    private static final String PHONE_PATTERN = "1[3-9]\\d{9}";

    private final FriendshipRepository friendshipRepository;
    private final FriendApplicationRepository applicationRepository;
    private final PlayerProfileRepository profileRepository;
    private final PlayerProfileService profileService;
    private final UserRepository userRepository;
    private final UserIdentityRepository identityRepository;

    public FriendSearchService(
            FriendshipRepository friendshipRepository,
            FriendApplicationRepository applicationRepository,
            PlayerProfileRepository profileRepository,
            PlayerProfileService profileService,
            UserRepository userRepository,
            UserIdentityRepository identityRepository) {
        this.friendshipRepository = friendshipRepository;
        this.applicationRepository = applicationRepository;
        this.profileRepository = profileRepository;
        this.profileService = profileService;
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
    }

    @Transactional
    public FriendSearchResult search(UUID userId, String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        UserEntity target = null;
        PlayerProfileEntity profile = null;
        if (query.matches(PUBLIC_ID_PATTERN)) {
            profile =
                    profileRepository
                            .findByPublicPlayerId(Long.parseLong(query))
                            .orElse(null);
            if (profile != null) {
                target =
                        userRepository
                                .findById(profile.getUserId())
                                .orElse(null);
            }
        } else if (query.matches(PHONE_PATTERN)) {
            String phoneNumber = MainlandPhoneNumber.parse(query).value();
            target =
                    identityRepository
                            .findByProviderAndProviderSubject(
                                    IdentityProvider.PHONE, phoneNumber)
                            .or(
                                    () ->
                                            identityRepository
                                                    .findByProviderAndProviderSubject(
                                                            IdentityProvider
                                                                    .ONE_TAP,
                                                            "phone:"
                                                                    + phoneNumber))
                            .map(UserIdentityEntity::getUser)
                            .orElse(null);
        }
        if (target == null) {
            throw new ApiException(ErrorCode.FRIEND_NOT_FOUND, "未找到该玩家");
        }
        if (target.getId().equals(userId)) {
            throw new ApiException(
                    ErrorCode.FRIEND_SELF_OPERATION, "不能对自己执行该操作");
        }
        if (profile == null) {
            profile = profileService.ensureProfile(target.getId());
        }
        return new FriendSearchResult(
                profile.getPublicPlayerId(),
                target.getDisplayName(),
                profile.getAvatarKey(),
                relationBetween(userId, target.getId()),
                target.getLastActiveAt());
    }

    private FriendRelation relationBetween(UUID userId, UUID targetId) {
        if (friendshipRepository.existsByUserIdAndFriendId(
                userId, targetId)) {
            return FriendRelation.FRIEND;
        }
        boolean pending =
                applicationRepository.existsByRequesterIdAndTargetIdAndStatus(
                                userId,
                                targetId,
                                FriendApplicationStatus.PENDING)
                        || applicationRepository
                                .existsByRequesterIdAndTargetIdAndStatus(
                                        targetId,
                                        userId,
                                        FriendApplicationStatus.PENDING);
        if (pending) {
            return FriendRelation.PENDING;
        }
        boolean rejected =
                applicationRepository.existsByRequesterIdAndTargetIdAndStatus(
                        userId, targetId, FriendApplicationStatus.REJECTED);
        return rejected ? FriendRelation.REJECTED : FriendRelation.NONE;
    }
}
