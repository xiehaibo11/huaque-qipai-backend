package com.nanbei.entertainment.backend.avatar.application;

import java.util.Optional;
import java.util.UUID;

public interface AvatarBlobStore {
    StoredAvatar save(UUID userId, NormalizedAvatar avatar);

    Optional<StoredAvatar> findByKey(String avatarKey);

    void deleteByUserId(UUID userId);
}
