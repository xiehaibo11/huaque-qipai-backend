package com.nanbei.entertainment.backend.timeloginact.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.timeloginact.application.TimeLoginResponses.ClaimResponse;
import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginOperationEntity;
import com.nanbei.entertainment.backend.timeloginact.infrastructure.TimeLoginOperationRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 领奖幂等日志：同一 Idempotency-Key 同请求回放既有结果，同键异请求返回冲突。
 * 与 fortune/room/shop 等既有链路同形。
 */
@Component
public class TimeLoginOperationLog {
    private final TimeLoginOperationRepository repository;
    private final ObjectMapper objectMapper;

    public TimeLoginOperationLog(
            TimeLoginOperationRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** 取回放结果；没有记录时返回 null，调用方随后执行真实用例。 */
    public ClaimResponse replay(UUID userId, String key, String requestHash) {
        repository.acquireOperationLock("time-login:" + userId + ':' + key);
        TimeLoginOperationEntity existing =
                repository.findByUserIdAndIdempotencyKey(userId, key).orElse(null);
        if (existing == null) {
            return null;
        }
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ApiException(
                    ErrorCode.TIME_LOGIN_IDEMPOTENCY_CONFLICT,
                    "Idempotency-Key 已用于不同的定时登录操作");
        }
        try {
            return objectMapper.readValue(existing.getResult(), ClaimResponse.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read time login operation", exception);
        }
    }

    public void record(
            UUID userId,
            String key,
            String requestHash,
            String operationType,
            ClaimResponse result,
            Instant occurredAt) {
        try {
            repository.save(
                    new TimeLoginOperationEntity(
                            userId,
                            key,
                            requestHash,
                            operationType,
                            objectMapper.writeValueAsString(result),
                            occurredAt));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to save time login operation", exception);
        }
    }

    public static String requireKey(String key) {
        if (key == null || key.isBlank() || key.trim().length() > 128) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key 不合法");
        }
        return key.trim();
    }
}
