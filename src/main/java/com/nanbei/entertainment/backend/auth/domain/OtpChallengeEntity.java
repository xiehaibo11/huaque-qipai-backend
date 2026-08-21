package com.nanbei.entertainment.backend.auth.domain;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "otp_challenges")
public class OtpChallengeEntity {
    @Id
    private UUID id;

    @Column(name = "phone_number", nullable = false, length = 32)
    private String phoneNumber;

    @Column(nullable = false, length = 20)
    private String purpose;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OtpChallengeEntity() {}

    public OtpChallengeEntity(
            String phoneNumber, String codeHash, Instant expiresAt, int maxAttempts) {
        this.id = UUID.randomUUID();
        this.phoneNumber = phoneNumber;
        this.purpose = "LOGIN";
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.maxAttempts = maxAttempts;
        this.createdAt = Instant.now();
    }

    public void verify(String submittedHash, Instant now) {
        if (consumedAt != null || !expiresAt.isAfter(now)) {
            throw new ApiException(ErrorCode.AUTH_OTP_EXPIRED, "验证码已过期");
        }
        if (attempts >= maxAttempts) {
            throw new ApiException(
                    ErrorCode.AUTH_OTP_ATTEMPTS_EXCEEDED, "验证码尝试次数已用尽");
        }
        attempts++;
        if (!codeHash.equals(submittedHash)) {
            throw new ApiException(
                    ErrorCode.AUTH_INVALID_CREDENTIAL, "手机号或验证码错误");
        }
        consumedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
