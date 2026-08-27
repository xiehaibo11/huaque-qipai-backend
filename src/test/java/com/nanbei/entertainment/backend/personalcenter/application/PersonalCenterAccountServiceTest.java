package com.nanbei.entertainment.backend.personalcenter.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.auth.application.OtpCodeGenerator;
import com.nanbei.entertainment.backend.auth.application.OtpRequestRateLimiter;
import com.nanbei.entertainment.backend.auth.application.OtpSender;
import com.nanbei.entertainment.backend.auth.domain.OtpChallengeEntity;
import com.nanbei.entertainment.backend.auth.infrastructure.OtpChallengeRepository;
import com.nanbei.entertainment.backend.auth.infrastructure.RefreshTokenRepository;
import com.nanbei.entertainment.backend.common.config.AuthProperties;
import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.user.domain.IdentityProvider;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import com.nanbei.entertainment.backend.user.domain.UserIdentityEntity;
import com.nanbei.entertainment.backend.user.domain.UserStatus;
import com.nanbei.entertainment.backend.user.infrastructure.UserIdentityRepository;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersonalCenterAccountServiceTest {
    @Mock CryptoService cryptoService;
    @Mock OtpSender otpSender;
    @Mock OtpCodeGenerator otpCodeGenerator;
    @Mock OtpRequestRateLimiter rateLimiter;
    @Mock OtpChallengeRepository otpRepository;
    @Mock RefreshTokenRepository refreshRepository;
    @Mock UserRepository userRepository;
    @Mock UserIdentityRepository identityRepository;

    PersonalCenterAccountService service;

    @BeforeEach
    void setUp() {
        service =
                new PersonalCenterAccountService(
                        new AuthProperties("123456", Duration.ofMinutes(5), 5),
                        cryptoService,
                        otpSender,
                        otpCodeGenerator,
                        rateLimiter,
                        otpRepository,
                        refreshRepository,
                        userRepository,
                        identityRepository);
    }

    @Test
    void requestsASeparatedPhoneBindingChallenge() {
        UUID userId = UUID.randomUUID();
        UserEntity user = UserEntity.create("测试用户");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(otpCodeGenerator.generate()).thenReturn("123456");
        when(cryptoService.sha256("13800138000:123456")).thenReturn("hash");
        when(otpRepository.save(any(OtpChallengeEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PersonalCenterAccountService.PhoneCodeResult result =
                service.requestPhoneCode(userId, "+86 138-0013-8000");

        ArgumentCaptor<OtpChallengeEntity> challenge =
                ArgumentCaptor.forClass(OtpChallengeEntity.class);
        verify(otpRepository).save(challenge.capture());
        assertThat(challenge.getValue().getPurpose()).isEqualTo("PHONE_BIND");
        assertThat(challenge.getValue().getPhoneNumber()).isEqualTo("13800138000");
        assertThat(result.expiresInSeconds()).isPositive();
        verify(rateLimiter).check("13800138000");
        verify(otpSender).send("13800138000", "123456");
    }

    @Test
    void requestsCodeWhenThePhoneBelongsToAnotherLoginIdentity() {
        UserEntity currentUser = UserEntity.create("微信用户");
        when(userRepository.findById(currentUser.getId()))
                .thenReturn(Optional.of(currentUser));
        when(otpCodeGenerator.generate()).thenReturn("123456");
        when(cryptoService.sha256("13800138000:123456"))
                .thenReturn("hash");
        when(otpRepository.save(any(OtpChallengeEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PersonalCenterAccountService.PhoneCodeResult result =
                service.requestPhoneCode(
                        currentUser.getId(), "13800138000");

        assertThat(result.expiresInSeconds()).isPositive();
        verify(otpSender).send("13800138000", "123456");
    }

    @Test
    void verifiesTheBindingChallengeAndRebindsTheCurrentPhoneIdentity() {
        UUID userId = UUID.randomUUID();
        UserEntity user = UserEntity.create("测试用户");
        UserIdentityEntity current =
                new UserIdentityEntity(
                        user, IdentityProvider.PHONE, "13900139000", "13900139000");
        OtpChallengeEntity challenge =
                new OtpChallengeEntity(
                        "13800138000", "PHONE_BIND", "hash", java.time.Instant.now().plusSeconds(300), 5);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(identityRepository.findByProviderAndProviderSubject(
                        IdentityProvider.PHONE, "13800138000"))
                .thenReturn(Optional.empty());
        when(identityRepository.findByUser_IdOrderByCreatedAtAsc(userId))
                .thenReturn(List.of(current));
        when(otpRepository.findFirstByPhoneNumberAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        "13800138000", "PHONE_BIND"))
                .thenReturn(Optional.of(challenge));
        when(cryptoService.sha256("13800138000:123456")).thenReturn("hash");

        PersonalCenterAccountService.PhoneBindingResult result =
                service.bindPhone(userId, "13800138000", "123456");

        assertThat(result.maskedPhone()).isEqualTo("138****8000");
        assertThat(result.reloginRequired()).isFalse();
        assertThat(current.getProviderSubject()).isEqualTo("13800138000");
        assertThat(current.getPhoneNumber()).isEqualTo("13800138000");
        verify(identityRepository).save(current);
    }

    @Test
    void refusesToMoveOnlyTheIdentityWhenPhoneAlreadyBelongsToAnotherAccount() {
        UserEntity currentUser = UserEntity.create("微信用户");
        UserEntity phoneUser = UserEntity.create("手机用户");
        UserIdentityEntity wechatIdentity =
                new UserIdentityEntity(
                        currentUser,
                        IdentityProvider.WECHAT,
                        "wechat-subject",
                        null);
        UserIdentityEntity phoneIdentity =
                new UserIdentityEntity(
                        phoneUser,
                        IdentityProvider.PHONE,
                        "13800138000",
                        "13800138000");
        OtpChallengeEntity challenge =
                new OtpChallengeEntity(
                        "13800138000",
                        "PHONE_BIND",
                        "hash",
                        java.time.Instant.now().plusSeconds(300),
                        5);
        when(userRepository.findById(currentUser.getId()))
                .thenReturn(Optional.of(currentUser));
        when(identityRepository.findByProviderAndProviderSubject(
                        IdentityProvider.PHONE, "13800138000"))
                .thenReturn(Optional.of(phoneIdentity));
        when(otpRepository.findFirstByPhoneNumberAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        "13800138000", "PHONE_BIND"))
                .thenReturn(Optional.of(challenge));
        when(cryptoService.sha256("13800138000:123456"))
                .thenReturn("hash");
        assertThatThrownBy(
                        () ->
                                service.bindPhone(
                                        currentUser.getId(),
                                        "13800138000",
                                        "123456"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        error ->
                                assertThat(error.code())
                                        .isEqualTo(ErrorCode.ACCOUNT_MERGE_REQUIRED));

        assertThat(wechatIdentity.getUser()).isSameAs(currentUser);
        verify(identityRepository, never()).saveAll(any());
    }

    @Test
    void refusesNonOriginalSixDigitBindingCodesBeforeHashVerification() {
        UserEntity currentUser = UserEntity.create("微信用户");
        OtpChallengeEntity challenge =
                new OtpChallengeEntity(
                        "13800138000",
                        "PHONE_BIND",
                        "hash",
                        java.time.Instant.now().plusSeconds(300),
                        5);
        when(userRepository.findById(currentUser.getId()))
                .thenReturn(Optional.of(currentUser));
        when(otpRepository.findFirstByPhoneNumberAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        "13800138000", "PHONE_BIND"))
                .thenReturn(Optional.of(challenge));

        assertThatThrownBy(
                        () ->
                                service.bindPhone(
                                        currentUser.getId(),
                                        "13800138000",
                                        "12345"))
                .isInstanceOf(ApiException.class);

        verify(cryptoService, never()).sha256("13800138000:12345");
        verify(identityRepository, never())
                .findByProviderAndProviderSubject(any(), any());
    }

    @Test
    void deactivatesTheAccountAndRevokesEveryRefreshToken() {
        UUID userId = UUID.randomUUID();
        UserEntity user = UserEntity.create("测试用户");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.deactivateAccount(userId);

        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(user.getAuthVersion()).isEqualTo(1L);
        verify(userRepository).save(user);
        verify(refreshRepository).acquireUserSessionLock("auth-session:" + userId);
        verify(refreshRepository).revokeAllByUserId(any(), any());
    }
}
