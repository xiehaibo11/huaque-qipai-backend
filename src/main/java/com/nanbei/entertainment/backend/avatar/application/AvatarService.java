package com.nanbei.entertainment.backend.avatar.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerProfileEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerProfileRepository;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvatarService {
    private static final String DEFAULT_AVATAR_KEY = "avatar_default";
    private static final Pattern AVATAR_KEY =
            Pattern.compile("avatar_[0-9a-fA-F-]{36}");

    private final AvatarImageNormalizer normalizer;
    private final AvatarBlobStore blobStore;
    private final UserRepository userRepository;
    private final PlayerProfileRepository profileRepository;

    public AvatarService(
            AvatarImageNormalizer normalizer,
            AvatarBlobStore blobStore,
            UserRepository userRepository,
            PlayerProfileRepository profileRepository) {
        this.normalizer = normalizer;
        this.blobStore = blobStore;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional
    public StoredAvatar save(
            UUID userId, byte[] sourceBytes, String declaredContentType) {
        UserEntity user =
                userRepository
                        .findById(userId)
                        .filter(UserEntity::isActive)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.AUTH_INVALID_CREDENTIAL,
                                                "用户不存在或已被禁用"));
        PlayerProfileEntity profile =
                profileRepository
                        .findById(userId)
                        .orElseGet(
                                () ->
                                        profileRepository.save(
                                                new PlayerProfileEntity(
                                                        user.getId(),
                                                        profileRepository.nextPublicPlayerId(),
                                                        DEFAULT_AVATAR_KEY,
                                                        0)));
        StoredAvatar stored =
                blobStore.save(
                        userId,
                        normalizer.normalize(sourceBytes, declaredContentType));
        profile.setAvatarKey(stored.avatarKey());
        profileRepository.save(profile);
        return stored;
    }

    @Transactional(readOnly = true)
    public StoredAvatar load(String avatarKey) {
        if (avatarKey == null || !AVATAR_KEY.matcher(avatarKey).matches()) {
            throw new ApiException(ErrorCode.AVATAR_NOT_FOUND, "头像不存在");
        }
        return blobStore
                .findByKey(avatarKey)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        ErrorCode.AVATAR_NOT_FOUND,
                                        "头像不存在"));
    }
}
