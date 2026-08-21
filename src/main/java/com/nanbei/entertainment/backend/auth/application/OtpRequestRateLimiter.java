package com.nanbei.entertainment.backend.auth.application;

import com.nanbei.entertainment.backend.auth.infrastructure.OtpChallengeRepository;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OtpRequestRateLimiter {
    private final OtpChallengeRepository repository;
    private final Clock clock;

    @Autowired
    public OtpRequestRateLimiter(OtpChallengeRepository repository) {
        this(repository, Clock.systemUTC());
    }

    OtpRequestRateLimiter(OtpChallengeRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void check(String phoneNumber) {
        Instant now = clock.instant();
        if (repository.countByPhoneNumberAndCreatedAtAfter(
                        phoneNumber, now.minusSeconds(60))
                >= 1) {
            reject("验证码发送过于频繁，请一分钟后再试");
        }
        if (repository.countByPhoneNumberAndCreatedAtAfter(
                        phoneNumber, now.minusSeconds(3_600))
                >= 5) {
            reject("该手机号本小时验证码次数已用尽");
        }
        if (repository.countByPhoneNumberAndCreatedAtAfter(
                        phoneNumber, now.minusSeconds(86_400))
                >= 10) {
            reject("该手机号今日验证码次数已用尽");
        }
    }

    private void reject(String message) {
        throw new ApiException(ErrorCode.AUTH_OTP_RATE_LIMITED, message);
    }
}
