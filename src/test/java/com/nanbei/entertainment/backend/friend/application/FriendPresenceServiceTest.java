package com.nanbei.entertainment.backend.friend.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.friend.domain.FriendPresenceState;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FriendPresenceServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-31T12:00:00Z");

    @Mock UserRepository userRepository;

    @Test
    void touchUpdatesLastActiveAtAndSavesTheUser() {
        UserEntity user = UserEntity.create("手机用户8000");
        when(userRepository.findById(user.getId()))
                .thenReturn(Optional.of(user));

        new FriendPresenceService(userRepository).touch(user.getId());

        assertThat(user.getLastActiveAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void touchIgnoresUnknownUsers() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        new FriendPresenceService(userRepository).touch(userId);

        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void reportsOnlineWithinTheFiveMinuteWindow() {
        FriendPresenceService service =
                new FriendPresenceService(userRepository);

        assertThat(service.isOnline(NOW, NOW)).isTrue();
        assertThat(
                        service.isOnline(
                                NOW.minus(
                                        FriendPresenceService.ONLINE_WINDOW),
                                NOW))
                .isTrue();
    }

    @Test
    void reportsOfflineBeyondTheWindowOrWithoutActivity() {
        FriendPresenceService service =
                new FriendPresenceService(userRepository);

        assertThat(
                        service.isOnline(
                                NOW.minus(
                                                FriendPresenceService
                                                        .ONLINE_WINDOW)
                                        .minusSeconds(1),
                                NOW))
                .isFalse();
        assertThat(service.isOnline(null, NOW)).isFalse();
        assertThat(service.stateOf(null))
                .isEqualTo(FriendPresenceState.OFFLINE);
    }
}
