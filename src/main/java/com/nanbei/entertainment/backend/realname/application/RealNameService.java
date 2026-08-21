package com.nanbei.entertainment.backend.realname.application;

import com.nanbei.entertainment.backend.common.config.AlipayRealNameProperties;
import com.nanbei.entertainment.backend.common.config.RealNameProperties;
import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.realname.domain.MainlandIdCardNumber;
import com.nanbei.entertainment.backend.realname.domain.RealNameFailedAttemptEntity;
import com.nanbei.entertainment.backend.realname.domain.RealNameMasker;
import com.nanbei.entertainment.backend.realname.domain.RealNameSource;
import com.nanbei.entertainment.backend.realname.domain.RealNameVerificationEntity;
import com.nanbei.entertainment.backend.realname.infrastructure.RealNameFailedAttemptRepository;
import com.nanbei.entertainment.backend.realname.infrastructure.RealNameVerificationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RealNameService {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(RealNameService.class);
    private static final Duration FAILED_ATTEMPT_WINDOW =
            Duration.ofHours(24);

    private final RealNameProperties properties;
    private final AlipayRealNameProperties alipayProperties;
    private final CryptoService cryptoService;
    private final RealNameVerifier verifier;
    private final AlipayRealNameExchanger alipayExchanger;
    private final RealNameVerificationRepository verificationRepository;
    private final RealNameFailedAttemptRepository failedAttemptRepository;

    public RealNameService(
            RealNameProperties properties,
            AlipayRealNameProperties alipayProperties,
            CryptoService cryptoService,
            RealNameVerifier verifier,
            AlipayRealNameExchanger alipayExchanger,
            RealNameVerificationRepository verificationRepository,
            RealNameFailedAttemptRepository failedAttemptRepository) {
        this.properties = properties;
        this.alipayProperties = alipayProperties;
        this.cryptoService = cryptoService;
        this.verifier = verifier;
        this.alipayExchanger = alipayExchanger;
        this.verificationRepository = verificationRepository;
        this.failedAttemptRepository = failedAttemptRepository;
        // 三个 RealNameVerifier 实现由 profile 与 nanbei.realname.enabled 互斥挑选，
        // 挑错时的表现和上游判定不一致完全相同（都是 REALNAME_MISMATCH），
        // 所以把实际注入的实现打在启动日志里，避免只能靠读部署配置反推。
        LOGGER.info(
                "Real-name verifier active: {}",
                verifier.getClass().getSimpleName());
    }

    @Transactional(readOnly = true)
    public RealNameStatus status(UUID userId) {
        return verificationRepository
                .findById(userId)
                .map(this::verifiedStatus)
                .orElseGet(
                        () ->
                                RealNameStatus.unverified(
                                        alipayProperties.isConfigured()));
    }

    @Transactional
    public RealNameStatus verifyManually(
            UUID userId, String realName, String idCardNumber) {
        return verify(userId, realName, idCardNumber, RealNameSource.MANUAL);
    }

    @Transactional
    public RealNameStatus verifyWithAlipay(UUID userId, String authCode) {
        AlipayRealName exchanged = alipayExchanger.exchange(authCode);
        return verify(
                userId,
                exchanged.realName(),
                exchanged.certNo(),
                RealNameSource.ALIPAY);
    }

    private RealNameStatus verify(
            UUID userId,
            String realName,
            String idCardNumber,
            RealNameSource source) {
        String trimmedName = realName == null ? "" : realName.trim();
        if (trimmedName.isEmpty()) {
            throw new ApiException(
                    ErrorCode.REALNAME_INVALID_FORMAT, "姓名不能为空");
        }
        MainlandIdCardNumber idCard = MainlandIdCardNumber.parse(idCardNumber);
        if (!idCard.isAdult()) {
            throw new ApiException(
                    ErrorCode.REALNAME_UNDERAGE,
                    "未满18周岁，无法完成实名认证");
        }
        Instant windowStart = Instant.now().minus(FAILED_ATTEMPT_WINDOW);
        long failures =
                failedAttemptRepository.countByUserIdAndFailedAtAfter(
                        userId, windowStart);
        if (failures >= properties.maxFailedAttempts()) {
            throw new ApiException(
                    ErrorCode.REALNAME_RATE_LIMITED,
                    "实名认证失败次数过多，请24小时后再试");
        }
        String idCardHmac =
                cryptoService.hmacSha256(
                        properties.hmacSecret(), idCard.value());
        Optional<RealNameVerificationEntity> existing =
                verificationRepository.findById(userId);
        if (existing.isPresent()) {
            // 已认证账号必须核对提交的证件是不是同一张：一致才是幂等重复提交，
            // 可以直接回已有快照；不一致说明在试图改绑另一个身份，这既没有经过
            // 核验也不会写库，绝不能回 VERIFIED 让客户端弹「认证成功」。
            if (Objects.equals(idCardHmac, existing.get().getIdCardHmac())) {
                return verifiedStatus(existing.get());
            }
            throw new ApiException(
                    ErrorCode.REALNAME_ALREADY_VERIFIED,
                    "该账号已完成实名认证，不可更换实名信息");
        }
        Optional<RealNameVerificationEntity> bound =
                verificationRepository.findByIdCardHmac(idCardHmac);
        if (bound.isPresent() && !bound.get().getUserId().equals(userId)) {
            throw new ApiException(
                    ErrorCode.REALNAME_ALREADY_BOUND,
                    "该身份证号已被其他账号绑定");
        }
        RealNameVerifyResult result =
                verifier.verify(trimmedName, idCard.value());
        switch (result) {
            case UNAVAILABLE ->
                    throw new ApiException(
                            ErrorCode.REALNAME_UNAVAILABLE,
                            "实名认证服务暂不可用，请稍后重试");
            case MISMATCH -> {
                failedAttemptRepository.save(
                        new RealNameFailedAttemptEntity(userId));
                throw new ApiException(
                        ErrorCode.REALNAME_MISMATCH, "姓名与身份证号不一致");
            }
            case MATCH -> {
                RealNameVerificationEntity saved =
                        verificationRepository.save(
                                new RealNameVerificationEntity(
                                        userId,
                                        RealNameMasker.maskName(trimmedName),
                                        idCardHmac,
                                        RealNameMasker.maskIdCard(
                                                idCard.value()),
                                        idCard.birthDate(),
                                        source));
                return verifiedStatus(saved);
            }
            default ->
                    throw new IllegalStateException(
                            "Unexpected verify result: " + result);
        }
    }

    private RealNameStatus verifiedStatus(RealNameVerificationEntity entity) {
        return new RealNameStatus(
                "VERIFIED",
                entity.getRealNameMasked(),
                entity.getIdCardMasked(),
                entity.getVerifiedAt(),
                alipayProperties.isConfigured());
    }
}
