package com.nanbei.entertainment.backend.wechatsubscription.application;

import com.nanbei.entertainment.backend.common.config.WeChatSubscriptionProperties;
import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.wechatsubscription.domain.WeChatSubscriptionGrantEntity;
import com.nanbei.entertainment.backend.wechatsubscription.domain.WeChatSubscriptionGrantStatus;
import com.nanbei.entertainment.backend.wechatsubscription.infrastructure.WeChatSubscriptionGrantRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeChatSubscriptionIntentService {
    private static final Duration INTENT_TTL = Duration.ofMinutes(10);

    private final WeChatSubscriptionGrantRepository repository;
    private final WeChatSubscriptionIdentityService identityService;
    private final CryptoService cryptoService;
    private final WeChatSubscriptionProperties properties;
    private final Clock clock;

    @Autowired
    public WeChatSubscriptionIntentService(
            WeChatSubscriptionGrantRepository repository,
            WeChatSubscriptionIdentityService identityService,
            CryptoService cryptoService,
            WeChatSubscriptionProperties properties) {
        this(
                repository,
                identityService,
                cryptoService,
                properties,
                Clock.systemUTC());
    }

    WeChatSubscriptionIntentService(
            WeChatSubscriptionGrantRepository repository,
            WeChatSubscriptionIdentityService identityService,
            CryptoService cryptoService,
            WeChatSubscriptionProperties properties,
            Clock clock) {
        this.repository = repository;
        this.identityService = identityService;
        this.cryptoService = cryptoService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public WeChatSubscriptionIntentResponse create(UUID userId) {
        requireEnabled();
        WeChatSubscriptionIdentity identity = identityService.requireCurrent(userId);
        String reserved = cryptoService.randomToken();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(INTENT_TTL);
        WeChatSubscriptionGrantEntity grant =
                repository.save(
                        new WeChatSubscriptionGrantEntity(
                                userId,
                                properties.templateId(),
                                properties.scene(),
                                cryptoService.sha256(reserved),
                                identity.subjectHash(),
                                expiresAt,
                                now));
        return new WeChatSubscriptionIntentResponse(
                grant.getId(),
                grant.getTemplateId(),
                grant.getScene(),
                reserved,
                expiresAt);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public WeChatSubscriptionCompleteResponse complete(
            UUID userId, UUID intentId, WeChatSubscriptionCompletion completion) {
        requireEnabled();
        WeChatSubscriptionGrantEntity grant =
                repository.findLockedById(intentId).orElseThrow(WeChatSubscriptionIntentService::invalid);
        if (!grant.getUserId().equals(userId)) {
            throw invalid();
        }
        validate(grant, completion);
        if (grant.getStatus() != WeChatSubscriptionGrantStatus.PENDING) {
            return response(grant);
        }
        Instant now = clock.instant();
        if (grant.isExpired(now)) {
            grant.expire(now);
            throw invalid();
        }
        if ("cancel".equals(completion.action())) {
            grant.cancel(now);
        } else if (completion.errCode() == 0) {
            grant.accept(now);
        } else {
            grant.deny(now);
        }
        return response(grant);
    }

    private void validate(
            WeChatSubscriptionGrantEntity grant,
            WeChatSubscriptionCompletion completion) {
        if (completion == null
                || !("confirm".equals(completion.action())
                        || "cancel".equals(completion.action()))
                || !properties.templateId().equals(completion.templateId())
                || completion.scene() != properties.scene()
                || completion.reserved() == null
                || completion.transaction() == null
                || !grant.getId().toString().equals(completion.transaction())
                || !cryptoService.constantTimeEquals(
                        grant.getReservedHash(),
                        cryptoService.sha256(completion.reserved()))) {
            throw invalid();
        }
        boolean successfulConfirm =
                "confirm".equals(completion.action()) && completion.errCode() == 0;
        if (successfulConfirm
                && (completion.openId() == null
                        || completion.openId().isBlank()
                        || !cryptoService.constantTimeEquals(
                                grant.getOpenIdSubjectHash(),
                                identityService.subjectHash(
                                        completion.openId())))) {
            throw invalid();
        }
    }

    private void requireEnabled() {
        if (!properties.configured()) {
            throw new ApiException(
                    ErrorCode.AUTH_PROVIDER_UNAVAILABLE, "微信一次性订阅尚未启用");
        }
    }

    private static WeChatSubscriptionCompleteResponse response(
            WeChatSubscriptionGrantEntity grant) {
        return new WeChatSubscriptionCompleteResponse(grant.getStatus());
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "微信订阅授权结果无效");
    }
}
