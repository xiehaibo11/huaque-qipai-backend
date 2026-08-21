package com.nanbei.entertainment.backend.auth.application;

import com.nanbei.entertainment.backend.auth.domain.OtpChallengeEntity;
import com.nanbei.entertainment.backend.auth.domain.RefreshTokenEntity;
import com.nanbei.entertainment.backend.auth.infrastructure.OtpChallengeRepository;
import com.nanbei.entertainment.backend.auth.infrastructure.RefreshTokenRepository;
import com.nanbei.entertainment.backend.common.config.AuthProperties;
import com.nanbei.entertainment.backend.common.config.SecurityProperties;
import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.common.security.JwtTokenService;
import com.nanbei.entertainment.backend.friend.application.FriendPresenceService;
import com.nanbei.entertainment.backend.user.domain.IdentityProvider;
import com.nanbei.entertainment.backend.user.domain.UserEntity;
import com.nanbei.entertainment.backend.user.domain.UserIdentityEntity;
import com.nanbei.entertainment.backend.user.infrastructure.UserIdentityRepository;
import com.nanbei.entertainment.backend.user.infrastructure.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {
    private final AuthProperties authProperties;
    private final SecurityProperties securityProperties;
    private final CryptoService cryptoService;
    private final JwtTokenService jwtTokenService;
    private final OtpSender otpSender;
    private final OtpCodeGenerator otpCodeGenerator;
    private final OtpRequestRateLimiter otpRequestRateLimiter;
    private final OtpChallengeRepository otpRepository;
    private final RefreshTokenRepository refreshRepository;
    private final UserRepository userRepository;
    private final UserIdentityRepository identityRepository;
    private final List<ExternalIdentityVerifier> externalVerifiers;
    private final FriendPresenceService friendPresenceService;

    public AuthenticationService(
            AuthProperties authProperties,
            SecurityProperties securityProperties,
            CryptoService cryptoService,
            JwtTokenService jwtTokenService,
            OtpSender otpSender,
            OtpCodeGenerator otpCodeGenerator,
            OtpRequestRateLimiter otpRequestRateLimiter,
            OtpChallengeRepository otpRepository,
            RefreshTokenRepository refreshRepository,
            UserRepository userRepository,
            UserIdentityRepository identityRepository,
            List<ExternalIdentityVerifier> externalVerifiers,
            FriendPresenceService friendPresenceService) {
        this.authProperties = authProperties;
        this.securityProperties = securityProperties;
        this.cryptoService = cryptoService;
        this.jwtTokenService = jwtTokenService;
        this.otpSender = otpSender;
        this.otpCodeGenerator = otpCodeGenerator;
        this.otpRequestRateLimiter = otpRequestRateLimiter;
        this.otpRepository = otpRepository;
        this.refreshRepository = refreshRepository;
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.externalVerifiers = externalVerifiers;
        this.friendPresenceService = friendPresenceService;
    }

    @Transactional
    public OtpChallengeEntity requestOtp(String rawPhoneNumber) {
        String phoneNumber = MainlandPhoneNumber.parse(rawPhoneNumber).value();
        otpRequestRateLimiter.check(phoneNumber);
        String code = otpCodeGenerator.generate();
        String hash =
                cryptoService.sha256(phoneNumber + ":" + code);
        OtpChallengeEntity challenge =
                new OtpChallengeEntity(
                        phoneNumber,
                        hash,
                        Instant.now().plus(authProperties.otpTtl()),
                        authProperties.maxAttempts());
        OtpChallengeEntity savedChallenge = otpRepository.save(challenge);
        otpSender.send(phoneNumber, code);
        return savedChallenge;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenPair verifyOtp(String rawPhoneNumber, String code) {
        String phoneNumber = MainlandPhoneNumber.parse(rawPhoneNumber).value();
        OtpChallengeEntity challenge =
                otpRepository
                        .findFirstByPhoneNumberAndConsumedAtIsNullOrderByCreatedAtDesc(
                                phoneNumber)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.AUTH_INVALID_CREDENTIAL,
                                                "手机号或验证码错误"));
        challenge.verify(
                cryptoService.sha256(phoneNumber + ":" + code),
                Instant.now());
        UserEntity user =
                findOrCreateUser(
                        IdentityProvider.PHONE,
                        phoneNumber,
                        phoneNumber,
                        "手机用户" + phoneNumber.substring(phoneNumber.length() - 4));
        friendPresenceService.touch(user.getId());
        return issueTokenPair(user, UUID.randomUUID());
    }

    @Transactional
    public TokenPair loginWithProvider(
            IdentityProvider provider, String credential) {
        if (provider == IdentityProvider.PHONE) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED, "PHONE 必须使用验证码入口");
        }
        ExternalIdentityVerifier verifier =
                externalVerifiers.stream()
                        .filter(candidate -> candidate.supports(provider))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.AUTH_PROVIDER_UNAVAILABLE,
                                                provider + " 适配器不存在"));
        ExternalIdentity identity = verifier.verify(provider, credential);
        UserEntity user =
                findOrCreateUser(
                        provider,
                        identity.subject(),
                        null,
                        provider == IdentityProvider.WECHAT
                                ? "微信用户"
                                : "一键登录用户");
        friendPresenceService.touch(user.getId());
        return issueTokenPair(user, UUID.randomUUID());
    }

    @Transactional
    public TokenPair loginLocalDeveloper() {
        UserEntity user =
                findOrCreateUser(
                        IdentityProvider.DEVELOPER,
                        "local-debug-developer",
                        null,
                        "开发账号");
        friendPresenceService.touch(user.getId());
        return issueTokenPair(user, UUID.randomUUID());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenPair refresh(String rawRefreshToken) {
        Instant now = Instant.now();
        RefreshTokenEntity current =
                refreshRepository
                        .findLockedByTokenHash(
                                cryptoService.sha256(rawRefreshToken))
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.AUTH_INVALID_CREDENTIAL,
                                                "Refresh Token 无效"));
        if (!current.isUsable(now)) {
            revokeFamily(current.getFamilyId(), now);
            throw new ApiException(
                    ErrorCode.AUTH_REFRESH_REUSED,
                    "Refresh Token 已使用、已撤销或已过期");
        }
        UserEntity user =
                userRepository
                        .findById(current.getUserId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.AUTH_INVALID_CREDENTIAL,
                                                "用户不存在"));
        ensureActive(user);
        friendPresenceService.touch(user.getId());

        String nextRaw = cryptoService.randomToken();
        RefreshTokenEntity next =
                new RefreshTokenEntity(
                        user.getId(),
                        cryptoService.sha256(nextRaw),
                        current.getFamilyId(),
                        now.plus(securityProperties.refreshTokenTtl()));
        refreshRepository.save(next);
        current.revoke(now, next.getId());
        return new TokenPair(
                jwtTokenService.createAccessToken(user),
                nextRaw,
                jwtTokenService.expiresInSeconds());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshRepository
                .findByTokenHash(cryptoService.sha256(rawRefreshToken))
                .ifPresent(token -> revokeFamily(token.getFamilyId(), Instant.now()));
    }

    private UserEntity findOrCreateUser(
            IdentityProvider provider,
            String subject,
            String phoneNumber,
            String displayName) {
        return identityRepository
                .findByProviderAndProviderSubject(provider, subject)
                .map(UserIdentityEntity::getUser)
                .orElseGet(
                        () -> {
                            UserEntity user =
                                    userRepository.save(UserEntity.create(displayName));
                            identityRepository.save(
                                    new UserIdentityEntity(
                                            user, provider, subject, phoneNumber));
                            return user;
                        });
    }

    private TokenPair issueTokenPair(UserEntity user, UUID familyId) {
        ensureActive(user);
        String rawRefresh = cryptoService.randomToken();
        refreshRepository.save(
                new RefreshTokenEntity(
                        user.getId(),
                        cryptoService.sha256(rawRefresh),
                        familyId,
                        Instant.now().plus(securityProperties.refreshTokenTtl())));
        return new TokenPair(
                jwtTokenService.createAccessToken(user),
                rawRefresh,
                jwtTokenService.expiresInSeconds());
    }

    private void revokeFamily(UUID familyId, Instant now) {
        refreshRepository
                .findByFamilyId(familyId)
                .forEach(token -> token.revoke(now, null));
    }

    private void ensureActive(UserEntity user) {
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.USER_DISABLED, "用户已被禁用");
        }
    }

}
