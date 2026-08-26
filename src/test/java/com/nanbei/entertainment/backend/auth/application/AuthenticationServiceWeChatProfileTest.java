package com.nanbei.entertainment.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.auth.infrastructure.OtpChallengeRepository;
import com.nanbei.entertainment.backend.auth.infrastructure.RefreshTokenRepository;
import com.nanbei.entertainment.backend.avatar.application.AvatarService;
import com.nanbei.entertainment.backend.common.config.AuthProperties;
import com.nanbei.entertainment.backend.common.config.SecurityProperties;
import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.profile.ProfileSource;
import com.nanbei.entertainment.backend.common.security.JwtTokenService;
import com.nanbei.entertainment.backend.friend.application.FriendPresenceService;
import com.nanbei.entertainment.backend.user.domain.IdentityProvider;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import com.nanbei.entertainment.backend.user.domain.UserIdentityEntity;
import com.nanbei.entertainment.backend.user.infrastructure.UserIdentityRepository;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceWeChatProfileTest {
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

    private UserEntity existingUser;
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        existingUser = UserEntity.create("微信用户");
        ExternalIdentityVerifier verifier =
                new ExternalIdentityVerifier() {
                    @Override
                    public boolean supports(IdentityProvider provider) {
                        return provider == IdentityProvider.WECHAT
                                || provider == IdentityProvider.ONE_TAP;
                    }

                    @Override
                    public ExternalIdentity verify(
                            IdentityProvider provider, String credential) {
                        return new ExternalIdentity(
                                provider,
                                provider == IdentityProvider.WECHAT
                                        ? "unionid:wechat-user-1"
                                        : "phone:13800138000",
                                "牌友昵称",
                                new byte[] {1, 2, 3},
                                "image/jpeg");
                    }
                };
        service =
                new AuthenticationService(
                        new AuthProperties(
                                "246810", Duration.ofMinutes(5), 5),
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
                        List.of(verifier),
                        externalAccountResolver,
                        friendPresenceService,
                        avatarService);
        when(externalAccountResolver.resolve(any(), any()))
                .thenReturn(existingUser);
        when(cryptoService.randomToken()).thenReturn("refresh-token");
        when(cryptoService.sha256("refresh-token"))
                .thenReturn("refresh-token-hash");
        when(jwtTokenService.createAccessToken(existingUser))
                .thenReturn("access-token");
        when(refreshRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void refreshesNicknameAndAvatarForExistingWechatUser() {
        service.loginWithProvider(
                IdentityProvider.WECHAT, "one-time-code");

        assertThat(existingUser.getDisplayName()).isEqualTo("牌友昵称");
        verify(avatarService)
                .saveFromWechat(
                        existingUser.getId(),
                        new byte[] {1, 2, 3},
                        "image/jpeg");
    }

    @Test
    void nonWechatProviderDoesNotMarkProfileAsWechatSourced() {
        service.loginWithProvider(
                IdentityProvider.ONE_TAP, "one-time-code");

        assertThat(existingUser.getDisplayName()).isEqualTo("牌友昵称");
        assertThat(existingUser.getDisplayNameSource()).isEqualTo(ProfileSource.SYSTEM);
        verify(avatarService)
                .save(
                        existingUser.getId(),
                        new byte[] {1, 2, 3},
                        "image/jpeg");
        verify(avatarService, never())
                .saveFromWechat(any(), any(), any());
    }
}
