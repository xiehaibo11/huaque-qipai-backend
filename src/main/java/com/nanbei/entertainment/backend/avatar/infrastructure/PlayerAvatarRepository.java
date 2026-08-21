package com.nanbei.entertainment.backend.avatar.infrastructure;

import com.nanbei.entertainment.backend.avatar.domain.PlayerAvatarEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerAvatarRepository
        extends JpaRepository<PlayerAvatarEntity, String> {
    Optional<PlayerAvatarEntity> findByUserId(UUID userId);
}
