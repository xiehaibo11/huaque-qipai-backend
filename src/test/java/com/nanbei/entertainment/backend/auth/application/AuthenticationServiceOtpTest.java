package com.nanbei.entertainment.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.auth.domain.OtpChallengeEntity;
import com.nanbei.entertainment.backend.auth.infrastructure.OtpChallengeRepository;
import com.nanbei.entertainment.backend.auth.infrastructure.RefreshTokenRepository;
import com.nanbei.entertainment.backend.avatar.application.AvatarService;
import com.nanbei.entertainment.backend.common.config.AuthProperties;
import com.nanbei.entertainment.backend.common.config.SecurityProperties;
import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.security.JwtTokenService;
import com.nanbei.entertainment.backend.friend.application.FriendPresenceService;
import com.nanbei.entertainment.backend.user.infrastructure.UserIdentityRepository;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceOtpTest {
    @Mock CryptoService cryptoService;
    @Mock JwtTokenService jwtTokenService;
    @Mock OtpSender otpSender;
    @Mock OtpCodeGenerator otpCodeGenerator;
    @Mock OtpRequestRateLimiter rateLimiter;
    @Mock OtpChallengeRepository otpRepository;
    @Mock RefreshTokenRepository refreshRepository;
    @Mock UserRepository userRepository;
    @Mock UserIdentityRepository identityRepository;
    @Mock FriendPresenceService friendPresenceService;
    @Mock AvatarService avatarService;
    @Mock ExternalIdentityAccountResolver externalAccountResolver;

    AuthenticationService service;

    @BeforeEach
    void setUp() {
        service =
                new AuthenticationService(
                        new AuthProperties("246810", Duration.ofMinutes(5), 5),
                        new SecurityProperties(
                                "01234567890123456789012345678901",
                                Duration.ofMinutes(15),
                                Duration.ofDays(30)),
                        cryptoService,
                        jwtTokenService,
                        otpSender,
                        otpCodeGenerator,
                        rateLimiter,
                        otpRepository,
                        refreshRepository,
                        userRepository,
                        List.of(),
                        externalAccountResolver,
                        friendPresenceService,
                        avatarService);
    }

    @Test
    void requestsOtpWithNormalizedPhoneAndGeneratedCode() {
        when(otpCodeGenerator.generate()).thenReturn("123456");
        when(cryptoService.sha256("13800138000:123456"))
                .thenReturn("a".repeat(64));
        when(otpRepository.save(any(OtpChallengeEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OtpChallengeEntity challenge = service.requestOtp("+86 138-0013-8000");

        assertThat(challenge).isNotNull();
        verify(rateLimiter).check("13800138000");
        InOrder order = inOrder(otpRepository, otpSender);
        order.verify(otpRepository).save(any(OtpChallengeEntity.class));
        order.verify(otpSender).send("13800138000", "123456");
    }

    @Test
    void loginNeverConsumesAPhoneBindingChallenge() {
        assertThatThrownBy(
                        () -> service.verifyOtp("13800138000", "123456"))
                .isInstanceOf(RuntimeException.class);

        verify(otpRepository)
                .findFirstByPhoneNumberAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        "13800138000", "LOGIN");
    }
}
