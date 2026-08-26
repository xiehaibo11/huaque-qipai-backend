package com.nanbei.entertainment.backend.common.security;

import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

final class ActiveUserJwtValidator implements OAuth2TokenValidator<Jwt> {
    private static final OAuth2Error INVALID =
            new OAuth2Error("invalid_token", "Authentication session is no longer active", null);

    private final UserRepository userRepository;

    ActiveUserJwtValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            UUID userId = UUID.fromString(jwt.getSubject());
            Number claim = jwt.getClaim("authVersion");
            long tokenVersion = claim == null ? 0L : claim.longValue();
            boolean valid =
                    userRepository
                            .findById(userId)
                            .filter(user -> user.isActive() && user.getAuthVersion() == tokenVersion)
                            .isPresent();
            return valid
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(INVALID);
        } catch (RuntimeException ignored) {
            return OAuth2TokenValidatorResult.failure(INVALID);
        }
    }
}
