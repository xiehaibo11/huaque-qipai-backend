package com.nanbei.entertainment.backend.realname.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nanbei.entertainment.backend.common.config.AlipayRealNameProperties;
import com.nanbei.entertainment.backend.common.config.RealNameProperties;
import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.realname.domain.RealNameFailedAttemptEntity;
import com.nanbei.entertainment.backend.realname.domain.RealNameSource;
import com.nanbei.entertainment.backend.realname.domain.RealNameVerificationEntity;
import com.nanbei.entertainment.backend.realname.infrastructure.RealNameFailedAttemptRepository;
import com.nanbei.entertainment.backend.realname.infrastructure.RealNameVerificationRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class RealNameServiceTest {
    private static final String VALID_ID_CARD = "110101199001011237";
    private static final String HMAC = "a".repeat(64);

    @Mock CryptoService cryptoService;
    @Mock RealNameVerifier verifier;
    @Mock AlipayRealNameExchanger alipayExchanger;
    @Mock RealNameVerificationRepository verificationRepository;
    @Mock RealNameFailedAttemptRepository failedAttemptRepository;

    RealNameService service;

    @BeforeEach
    void setUp() {
        service =
                new RealNameService(
                        new RealNameProperties(
                                false, "", "", "test-secret", 5),
                        new AlipayRealNameProperties(false, "", "", ""),
                        cryptoService,
                        verifier,
                        alipayExchanger,
                        verificationRepository,
                        failedAttemptRepository);
    }

    /**
     * 三个 verifier Bean 由 profile 与 nanbei.realname.enabled 互斥挑选，运行时以前无从确认
     * 实际生效的是哪一个；生产上「local 桩把所有真实证件判为不一致」与「上游判定不一致」
     * 表现完全相同，只能靠读部署配置反推。启动日志把它固定下来。
     */
    @Test
    void logsWhichVerifierImplementationIsActiveAtStartup() {
        Logger logger = (Logger) LoggerFactory.getLogger(RealNameService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        new RealNameService(
                new RealNameProperties(false, "", "", "test-secret", 5),
                new AlipayRealNameProperties(false, "", "", ""),
                cryptoService,
                new StubRealNameVerifier(),
                alipayExchanger,
                verificationRepository,
                failedAttemptRepository);

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(
                        message ->
                                assertThat(message)
                                        .contains("Real-name verifier active")
                                        .contains("StubRealNameVerifier"));
    }

    private static final class StubRealNameVerifier implements RealNameVerifier {
        @Override
        public RealNameVerifyResult verify(String realName, String idCardNumber) {
            return RealNameVerifyResult.MATCH;
        }
    }

    @Test
    void reportsUnverifiedStatusWithoutVerificationRecord() {
        UUID userId = UUID.randomUUID();
        when(verificationRepository.findById(userId))
                .thenReturn(Optional.empty());

        RealNameStatus status = service.status(userId);

        assertThat(status.status()).isEqualTo("UNVERIFIED");
        assertThat(status.realNameMasked()).isNull();
        assertThat(status.alipayOneTapEnabled()).isFalse();
    }

    @Test
    void verifiesAndPersistsMaskedSnapshotOnMatch() {
        UUID userId = UUID.randomUUID();
        when(verificationRepository.findById(userId))
                .thenReturn(Optional.empty());
        when(cryptoService.hmacSha256("test-secret", VALID_ID_CARD))
                .thenReturn(HMAC);
        when(verificationRepository.findByIdCardHmac(HMAC))
                .thenReturn(Optional.empty());
        when(verifier.verify("张测试", VALID_ID_CARD))
                .thenReturn(RealNameVerifyResult.MATCH);
        when(verificationRepository.save(
                        any(RealNameVerificationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RealNameStatus snapshot =
                service.verifyManually(userId, "张测试", VALID_ID_CARD);

        assertThat(snapshot.status()).isEqualTo("VERIFIED");
        assertThat(snapshot.realNameMasked()).isEqualTo("张**");
        assertThat(snapshot.idCardMasked())
                .isEqualTo("1101**********1237");
        ArgumentCaptor<RealNameVerificationEntity> captor =
                ArgumentCaptor.forClass(RealNameVerificationEntity.class);
        verify(verificationRepository).save(captor.capture());
        RealNameVerificationEntity saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getIdCardHmac()).isEqualTo(HMAC);
        assertThat(saved.getBirthDate())
                .isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(saved.getSource()).isEqualTo(RealNameSource.MANUAL);
    }

    @Test
    void returnsExistingSnapshotIdempotentlyForVerifiedUser() {
        UUID userId = UUID.randomUUID();
        RealNameVerificationEntity entity =
                new RealNameVerificationEntity(
                        userId,
                        "张**",
                        HMAC,
                        "1101**********1237",
                        LocalDate.of(1990, 1, 1),
                        RealNameSource.MANUAL);
        when(verificationRepository.findById(userId))
                .thenReturn(Optional.of(entity));
        // 幂等的前提是「重复提交的是同一张证件」，所以必须先核对哈希。
        when(cryptoService.hmacSha256("test-secret", VALID_ID_CARD))
                .thenReturn(HMAC);

        RealNameStatus snapshot =
                service.verifyManually(userId, "张测试", VALID_ID_CARD);

        assertThat(snapshot.status()).isEqualTo("VERIFIED");
        assertThat(snapshot.realNameMasked()).isEqualTo("张**");
        verify(verifier, never()).verify(anyString(), anyString());
        verify(verificationRepository, never())
                .save(any(RealNameVerificationEntity.class));
    }

    /**
     * 已认证账号提交另一张证件时，以前直接短路返回已有快照，接口回 200 + VERIFIED，
     * 客户端据此弹「认证成功」——但服务端根本没核对提交的身份，也没有改动任何记录。
     * 无论提交什么都显示"已认证"，正是这条短路造成的。
     */
    @Test
    void rejectsADifferentIdentityOnAnAlreadyVerifiedAccount() {
        UUID userId = UUID.randomUUID();
        String boundHmac = "b".repeat(64);
        when(verificationRepository.findById(userId))
                .thenReturn(
                        Optional.of(
                                new RealNameVerificationEntity(
                                        userId,
                                        "张**",
                                        boundHmac,
                                        "1101**********1237",
                                        LocalDate.of(1990, 1, 1),
                                        RealNameSource.MANUAL)));
        when(cryptoService.hmacSha256("test-secret", VALID_ID_CARD))
                .thenReturn(HMAC);

        assertThatThrownBy(
                        () ->
                                service.verifyManually(
                                        userId, "李测试", VALID_ID_CARD))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.REALNAME_ALREADY_VERIFIED);
        verify(verifier, never()).verify(anyString(), anyString());
        verify(verificationRepository, never())
                .save(any(RealNameVerificationEntity.class));
    }

    /** 换证被拒不是核验失败，不应该占用 24 小时内的失败次数配额。 */
    @Test
    void rejectingAnIdentityChangeDoesNotBurnTheFailedAttemptQuota() {
        UUID userId = UUID.randomUUID();
        when(verificationRepository.findById(userId))
                .thenReturn(
                        Optional.of(
                                new RealNameVerificationEntity(
                                        userId,
                                        "张**",
                                        "b".repeat(64),
                                        "1101**********1237",
                                        LocalDate.of(1990, 1, 1),
                                        RealNameSource.MANUAL)));
        when(cryptoService.hmacSha256("test-secret", VALID_ID_CARD))
                .thenReturn(HMAC);

        assertThatThrownBy(
                        () ->
                                service.verifyManually(
                                        userId, "李测试", VALID_ID_CARD))
                .isInstanceOf(ApiException.class);

        verify(failedAttemptRepository, never())
                .save(any(RealNameFailedAttemptEntity.class));
    }

    @Test
    void rejectsIdCardAlreadyBoundToAnotherAccount() {
        UUID userId = UUID.randomUUID();
        when(verificationRepository.findById(userId))
                .thenReturn(Optional.empty());
        when(cryptoService.hmacSha256("test-secret", VALID_ID_CARD))
                .thenReturn(HMAC);
        when(verificationRepository.findByIdCardHmac(HMAC))
                .thenReturn(
                        Optional.of(
                                new RealNameVerificationEntity(
                                        UUID.randomUUID(),
                                        "李*",
                                        HMAC,
                                        "1101**********1237",
                                        LocalDate.of(1990, 1, 1),
                                        RealNameSource.MANUAL)));

        assertThatThrownBy(
                        () ->
                                service.verifyManually(
                                        userId, "张测试", VALID_ID_CARD))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.REALNAME_ALREADY_BOUND);
        verify(verifier, never()).verify(anyString(), anyString());
    }

    @Test
    void rateLimitsAfterTooManyFailedAttemptsWithin24Hours() {
        UUID userId = UUID.randomUUID();
        when(failedAttemptRepository.countByUserIdAndFailedAtAfter(
                        eq(userId), any()))
                .thenReturn(5L);

        assertThatThrownBy(
                        () ->
                                service.verifyManually(
                                        userId, "张测试", VALID_ID_CARD))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.REALNAME_RATE_LIMITED);
        verify(verifier, never()).verify(anyString(), anyString());
    }

    @Test
    void recordsFailedAttemptWhenNameAndIdCardMismatch() {
        UUID userId = UUID.randomUUID();
        when(verificationRepository.findById(userId))
                .thenReturn(Optional.empty());
        when(cryptoService.hmacSha256("test-secret", VALID_ID_CARD))
                .thenReturn(HMAC);
        when(verificationRepository.findByIdCardHmac(HMAC))
                .thenReturn(Optional.empty());
        when(verifier.verify("张测试", VALID_ID_CARD))
                .thenReturn(RealNameVerifyResult.MISMATCH);

        assertThatThrownBy(
                        () ->
                                service.verifyManually(
                                        userId, "张测试", VALID_ID_CARD))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.REALNAME_MISMATCH);
        verify(failedAttemptRepository)
                .save(any(RealNameFailedAttemptEntity.class));
        verify(verificationRepository, never())
                .save(any(RealNameVerificationEntity.class));
    }

    @Test
    void reportsUnavailableWhenUpstreamVerifierIsUnavailable() {
        UUID userId = UUID.randomUUID();
        when(verificationRepository.findById(userId))
                .thenReturn(Optional.empty());
        when(cryptoService.hmacSha256("test-secret", VALID_ID_CARD))
                .thenReturn(HMAC);
        when(verificationRepository.findByIdCardHmac(HMAC))
                .thenReturn(Optional.empty());
        when(verifier.verify("张测试", VALID_ID_CARD))
                .thenReturn(RealNameVerifyResult.UNAVAILABLE);

        assertThatThrownBy(
                        () ->
                                service.verifyManually(
                                        userId, "张测试", VALID_ID_CARD))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.REALNAME_UNAVAILABLE);
    }

    @Test
    void rejectsUnderageCardHoldersBeforeUpstreamVerification() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                service.verifyManually(
                                        userId, "张测试", "110101201506011232"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.REALNAME_UNDERAGE);
        verify(verifier, never()).verify(anyString(), anyString());
    }

    @Test
    void rejectsMalformedIdCardNumbers() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                service.verifyManually(
                                        userId, "张测试", "110101199001011238"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.REALNAME_INVALID_FORMAT);
    }

    @Test
    void verifiesThroughAlipayWithAlipaySource() {
        UUID userId = UUID.randomUUID();
        when(alipayExchanger.exchange("auth-code"))
                .thenReturn(new AlipayRealName("张测试", VALID_ID_CARD));
        when(verificationRepository.findById(userId))
                .thenReturn(Optional.empty());
        when(cryptoService.hmacSha256("test-secret", VALID_ID_CARD))
                .thenReturn(HMAC);
        when(verificationRepository.findByIdCardHmac(HMAC))
                .thenReturn(Optional.empty());
        when(verifier.verify("张测试", VALID_ID_CARD))
                .thenReturn(RealNameVerifyResult.MATCH);
        when(verificationRepository.save(
                        any(RealNameVerificationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RealNameStatus snapshot =
                service.verifyWithAlipay(userId, "auth-code");

        assertThat(snapshot.status()).isEqualTo("VERIFIED");
        assertThat(snapshot.realNameMasked()).isEqualTo("张**");
        ArgumentCaptor<RealNameVerificationEntity> captor =
                ArgumentCaptor.forClass(RealNameVerificationEntity.class);
        verify(verificationRepository).save(captor.capture());
        assertThat(captor.getValue().getSource())
                .isEqualTo(RealNameSource.ALIPAY);
    }
}
