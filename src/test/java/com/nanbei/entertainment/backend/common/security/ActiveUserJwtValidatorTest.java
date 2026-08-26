package com.nanbei.entertainment.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.user.domain.UserEntity;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class ActiveUserJwtValidatorTest {
    @Mock UserRepository userRepository;

    @Test
    void invalidatedSessionVersionRejectsOldAccessToken() {
        UserEntity user = UserEntity.create("用户");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        Jwt token = token(user, 0L);
        user.invalidateSessions();

        assertThat(new ActiveUserJwtValidator(userRepository).validate(token).hasErrors())
                .isTrue();
    }

    @Test
    void deploymentKeepsLegacyVersionZeroTokensCompatible() {
        UserEntity user = UserEntity.create("用户");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        Jwt token = token(user, null);

        assertThat(new ActiveUserJwtValidator(userRepository).validate(token).hasErrors())
                .isFalse();
    }

    private static Jwt token(UserEntity user, Long authVersion) {
        Map<String, Object> claims =
                authVersion == null
                        ? Map.of("sub", user.getId().toString())
                        : Map.of(
                                "sub", user.getId().toString(),
                                "authVersion", authVersion);
        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(900),
                Map.of("alg", "HS256"),
                claims);
    }
}
