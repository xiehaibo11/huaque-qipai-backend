package com.nanbei.entertainment.backend.user.api;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerProfileRepository;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserRepository userRepository;
    private final PlayerProfileRepository profileRepository;

    public UserController(
            UserRepository userRepository,
            PlayerProfileRepository profileRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UserEntity user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.AUTH_INVALID_CREDENTIAL,
                                                "用户不存在"));
        Long publicPlayerId =
                profileRepository
                        .findById(userId)
                        .map(profile -> profile.getPublicPlayerId())
                        .orElse(null);
        return new UserResponse(
                user.getId(),
                user.getDisplayName(),
                user.getStatus().name(),
                user.getCreatedAt(),
                publicPlayerId);
    }

    public record UserResponse(
            UUID id,
            String displayName,
            String status,
            Instant createdAt,
            Long publicPlayerId) {}
}
