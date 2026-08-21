package com.nanbei.entertainment.backend.gamehome.application;

import com.nanbei.entertainment.backend.gamehome.domain.PlayerProfileEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerProfileRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PlayerProfileService {
    private static final String DEFAULT_AVATAR_KEY = "avatar_default";

    private final PlayerProfileRepository profileRepository;

    public PlayerProfileService(PlayerProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    public PlayerProfileEntity ensureProfile(UUID userId) {
        return profileRepository
                .findById(userId)
                .orElseGet(
                        () ->
                                profileRepository.save(
                                        new PlayerProfileEntity(
                                                userId,
                                                profileRepository
                                                        .nextPublicPlayerId(),
                                                DEFAULT_AVATAR_KEY,
                                                0)));
    }
}
