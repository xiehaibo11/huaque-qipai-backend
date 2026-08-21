package com.nanbei.entertainment.backend.avatar.infrastructure;

import com.nanbei.entertainment.backend.avatar.application.AvatarBlobStore;
import com.nanbei.entertainment.backend.avatar.application.NormalizedAvatar;
import com.nanbei.entertainment.backend.avatar.application.StoredAvatar;
import com.nanbei.entertainment.backend.avatar.domain.PlayerAvatarEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresAvatarBlobStore implements AvatarBlobStore {
    private final PlayerAvatarRepository repository;

    public PostgresAvatarBlobStore(PlayerAvatarRepository repository) {
        this.repository = repository;
    }

    @Override
    public StoredAvatar save(UUID userId, NormalizedAvatar avatar) {
        PlayerAvatarEntity entity =
                repository.findByUserId(userId)
                        .map(
                                existing -> {
                                    existing.update(avatar);
                                    return existing;
                                })
                        .orElseGet(
                                () ->
                                        new PlayerAvatarEntity(
                                                "avatar_" + UUID.randomUUID(),
                                                userId,
                                                avatar));
        return toStored(repository.save(entity));
    }

    @Override
    public Optional<StoredAvatar> findByKey(String avatarKey) {
        return repository.findById(avatarKey).map(PostgresAvatarBlobStore::toStored);
    }

    private static StoredAvatar toStored(PlayerAvatarEntity entity) {
        return new StoredAvatar(
                entity.getAvatarKey(),
                entity.getUserId(),
                entity.getImageBytes(),
                entity.getContentType(),
                entity.getSha256(),
                entity.getWidth(),
                entity.getHeight(),
                entity.getUpdatedAt());
    }
}
