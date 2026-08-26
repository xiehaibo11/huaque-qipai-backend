package com.nanbei.entertainment.backend.avatar.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.profile.ProfileSource;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerProfileEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerProfileRepository;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvatarServiceWechatProfileTest {
    @Mock AvatarImageNormalizer normalizer;
    @Mock AvatarBlobStore blobStore;
    @Mock UserRepository userRepository;
    @Mock PlayerProfileRepository profileRepository;

    private AvatarService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service =
                new AvatarService(
                        normalizer,
                        blobStore,
                        userRepository,
                        profileRepository);
        userId = UUID.randomUUID();
    }

    @Test
    void wechatRefreshDoesNotOverwriteUserSelectedAvatar() {
        PlayerProfileEntity profile = profile(ProfileSource.USER);
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));

        service.saveFromWechat(userId, new byte[] {1}, "image/jpeg");

        verify(normalizer, never()).normalize(new byte[] {1}, "image/jpeg");
        verify(blobStore, never()).save(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        assertThat(profile.getAvatarKey()).isEqualTo("avatar_user");
    }

    @Test
    void authorizationChangeClearsOnlyWechatSelectedAvatar() {
        PlayerProfileEntity profile = profile(ProfileSource.WECHAT);
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));

        service.clearWechatAvatar(userId);

        verify(blobStore).deleteByUserId(userId);
        verify(profileRepository).save(profile);
        assertThat(profile.getAvatarKey()).isEqualTo("avatar_default");
        assertThat(profile.getAvatarSource()).isEqualTo(ProfileSource.SYSTEM);
    }

    private PlayerProfileEntity profile(ProfileSource source) {
        PlayerProfileEntity profile =
                new PlayerProfileEntity(userId, 10001L, "avatar_user", 0);
        profile.setAvatar("avatar_user", source);
        return profile;
    }
}
