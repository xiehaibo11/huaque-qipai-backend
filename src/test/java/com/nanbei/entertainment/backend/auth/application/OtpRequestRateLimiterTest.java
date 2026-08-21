package com.nanbei.entertainment.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.auth.infrastructure.OtpChallengeRepository;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OtpRequestRateLimiterTest {
    private static final Instant NOW = Instant.parse("2026-07-26T12:00:00Z");
    private static final String PHONE = "13800138000";

    @Mock OtpChallengeRepository repository;

    OtpRequestRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter =
                new OtpRequestRateLimiter(
                        repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void rejectsSecondRequestWithinOneMinute() {
        when(repository.countByPhoneNumberAndCreatedAtAfter(
                        PHONE, NOW.minusSeconds(60)))
                .thenReturn(1L);

        assertRateLimited();
    }

    @Test
    void rejectsSixthRequestWithinOneHour() {
        when(repository.countByPhoneNumberAndCreatedAtAfter(
                        PHONE, NOW.minusSeconds(60)))
                .thenReturn(0L);
        when(repository.countByPhoneNumberAndCreatedAtAfter(
                        PHONE, NOW.minusSeconds(3_600)))
                .thenReturn(5L);

        assertRateLimited();
    }

    @Test
    void rejectsEleventhRequestWithinOneDay() {
        when(repository.countByPhoneNumberAndCreatedAtAfter(
                        PHONE, NOW.minusSeconds(60)))
                .thenReturn(0L);
        when(repository.countByPhoneNumberAndCreatedAtAfter(
                        PHONE, NOW.minusSeconds(3_600)))
                .thenReturn(4L);
        when(repository.countByPhoneNumberAndCreatedAtAfter(
                        PHONE, NOW.minusSeconds(86_400)))
                .thenReturn(10L);

        assertRateLimited();
    }

    @Test
    void permitsRequestWithinAllLimits() {
        when(repository.countByPhoneNumberAndCreatedAtAfter(
                        PHONE, NOW.minusSeconds(60)))
                .thenReturn(0L);
        when(repository.countByPhoneNumberAndCreatedAtAfter(
                        PHONE, NOW.minusSeconds(3_600)))
                .thenReturn(4L);
        when(repository.countByPhoneNumberAndCreatedAtAfter(
                        PHONE, NOW.minusSeconds(86_400)))
                .thenReturn(9L);

        assertThatCode(() -> rateLimiter.check(PHONE)).doesNotThrowAnyException();
    }

    private void assertRateLimited() {
        assertThatThrownBy(() -> rateLimiter.check(PHONE))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.code())
                                        .isEqualTo(ErrorCode.AUTH_OTP_RATE_LIMITED));
    }
}
