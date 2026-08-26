package com.nanbei.entertainment.backend.personalcenter.application;

import com.nanbei.entertainment.backend.auth.application.MainlandPhoneNumber;
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
import com.nanbei.entertainment.backend.user.infrastructure.UserIdentityRepository;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalCenterAccountService {
    private static final String PHONE_BIND = "PHONE_BIND";

    private final AuthProperties authProperties;
    private final CryptoService cryptoService;
    private final OtpSender otpSender;
    private final OtpCodeGenerator otpCodeGenerator;
    private final OtpRequestRateLimiter rateLimiter;
    private final OtpChallengeRepository otpRepository;
    private final RefreshTokenRepository refreshRepository;
    private final UserRepository userRepository;
    private final UserIdentityRepository identityRepository;

    public PersonalCenterAccountService(
            AuthProperties authProperties,
            CryptoService cryptoService,
            OtpSender otpSender,
            OtpCodeGenerator otpCodeGenerator,
            OtpRequestRateLimiter rateLimiter,
            OtpChallengeRepository otpRepository,
            RefreshTokenRepository refreshRepository,
            UserRepository userRepository,
            UserIdentityRepository identityRepository) {
        this.authProperties = authProperties;
        this.cryptoService = cryptoService;
        this.otpSender = otpSender;
        this.otpCodeGenerator = otpCodeGenerator;
        this.rateLimiter = rateLimiter;
        this.otpRepository = otpRepository;
        this.refreshRepository = refreshRepository;
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
    }

    @Transactional
    public PhoneCodeResult requestPhoneCode(UUID userId, String rawPhoneNumber) {
        activeUser(userId);
        String phoneNumber = MainlandPhoneNumber.parse(rawPhoneNumber).value();
        rateLimiter.check(phoneNumber);
        String code = otpCodeGenerator.generate();
        Instant expiresAt = Instant.now().plus(authProperties.otpTtl());
        OtpChallengeEntity challenge =
                new OtpChallengeEntity(
                        phoneNumber,
                        PHONE_BIND,
                        cryptoService.sha256(phoneNumber + ":" + code),
                        expiresAt,
                        authProperties.maxAttempts());
        otpRepository.save(challenge);
        otpSender.send(phoneNumber, code);
        return new PhoneCodeResult(
                Math.max(1L, ChronoUnit.SECONDS.between(Instant.now(), expiresAt)));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public PhoneBindingResult bindPhone(UUID userId, String rawPhoneNumber, String code) {
        UserEntity user = activeUser(userId);
        String phoneNumber = MainlandPhoneNumber.parse(rawPhoneNumber).value();
        OtpChallengeEntity challenge =
                otpRepository
                        .findFirstByPhoneNumberAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                                phoneNumber, PHONE_BIND)
                        .orElseThrow(this::invalidCode);
        challenge.verify(
                cryptoService.sha256(phoneNumber + ":" + normalizeCode(code)),
                Instant.now());
        UserIdentityEntity boundIdentity =
                identityRepository
                        .findByProviderAndProviderSubject(
                                IdentityProvider.PHONE, phoneNumber)
                        .orElse(null);
        if (boundIdentity != null
                && !boundIdentity.getUser().getId().equals(userId)) {
            throw new ApiException(
                    ErrorCode.ACCOUNT_MERGE_REQUIRED,
                    "该手机号已属于其他账号，请联系客服完成资产合并");
        }
        UserIdentityEntity identity =
                identityRepository.findByUser_IdOrderByCreatedAtAsc(userId).stream()
                        .filter(item -> item.getProvider() == IdentityProvider.PHONE)
                        .findFirst()
                        .orElseGet(
                                () ->
                                        new UserIdentityEntity(
                                                user,
                                                IdentityProvider.PHONE,
                                                phoneNumber,
                                                phoneNumber));
        identity.rebindPhone(phoneNumber);
        identityRepository.save(identity);
        return new PhoneBindingResult(mask(phoneNumber), false);
    }

    @Transactional
    public void deactivateAccount(UUID userId) {
        UserEntity user = activeUser(userId);
        user.deactivate();
        user.invalidateSessions();
        userRepository.save(user);
        revokeRefreshTokens(userId);
    }

    private UserEntity activeUser(UUID userId) {
        UserEntity user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.AUTH_INVALID_CREDENTIAL,
                                                "用户不存在"));
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.USER_DISABLED, "账号已停用");
        }
        return user;
    }

    private void revokeRefreshTokens(UUID userId) {
        refreshRepository.acquireUserSessionLock("auth-session:" + userId);
        refreshRepository.revokeAllByUserId(userId, Instant.now());
    }

    private String normalizeCode(String code) {
        String normalized = code == null ? "" : code.trim();
        if (!normalized.matches("\\d{4,8}")) {
            throw invalidCode();
        }
        return normalized;
    }

    private ApiException invalidCode() {
        return new ApiException(ErrorCode.AUTH_INVALID_CREDENTIAL, "手机号或验证码错误");
    }

    private static String mask(String phoneNumber) {
        return phoneNumber.substring(0, 3) + "****" + phoneNumber.substring(7);
    }

    public record PhoneCodeResult(long expiresInSeconds) {}

    public record PhoneBindingResult(
            String maskedPhone, boolean reloginRequired) {}
}
