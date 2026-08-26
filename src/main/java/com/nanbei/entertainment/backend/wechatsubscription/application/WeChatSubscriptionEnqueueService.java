package com.nanbei.entertainment.backend.wechatsubscription.application;

import com.nanbei.entertainment.backend.common.config.WeChatSubscriptionProperties;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.wechatsubscription.domain.WeChatSubscriptionDeliveryEntity;
import com.nanbei.entertainment.backend.wechatsubscription.domain.WeChatSubscriptionGrantEntity;
import com.nanbei.entertainment.backend.wechatsubscription.infrastructure.WeChatSubscriptionDeliveryRepository;
import com.nanbei.entertainment.backend.wechatsubscription.infrastructure.WeChatSubscriptionGrantRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeChatSubscriptionEnqueueService {
    private final WeChatSubscriptionGrantRepository grantRepository;
    private final WeChatSubscriptionDeliveryRepository deliveryRepository;
    private final WeChatSubscriptionProperties properties;
    private final Clock clock;

    @Autowired
    public WeChatSubscriptionEnqueueService(
            WeChatSubscriptionGrantRepository grantRepository,
            WeChatSubscriptionDeliveryRepository deliveryRepository,
            WeChatSubscriptionProperties properties) {
        this(grantRepository, deliveryRepository, properties, Clock.systemUTC());
    }

    WeChatSubscriptionEnqueueService(
            WeChatSubscriptionGrantRepository grantRepository,
            WeChatSubscriptionDeliveryRepository deliveryRepository,
            WeChatSubscriptionProperties properties,
            Clock clock) {
        this.grantRepository = grantRepository;
        this.deliveryRepository = deliveryRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public WeChatSubscriptionDeliveryEntity enqueue(
            UUID userId,
            String eventType,
            String eventId,
            String title,
            String content,
            String url) {
        if (!properties.configured()) {
            throw new ApiException(
                    ErrorCode.AUTH_PROVIDER_UNAVAILABLE, "微信一次性订阅尚未启用");
        }
        String lockKey =
                userId
                        + ":"
                        + properties.templateId()
                        + ":"
                        + eventType
                        + ":"
                        + eventId;
        deliveryRepository.acquireEventLock(lockKey);
        var existing =
                deliveryRepository.findByUserIdAndTemplateIdAndEventTypeAndEventId(
                        userId, properties.templateId(), eventType, eventId);
        if (existing.isPresent()) {
            return existing.get();
        }
        WeChatSubscriptionGrantEntity grant =
                grantRepository
                        .findOldestAvailableLocked(
                                userId, properties.templateId(), properties.scene())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.VALIDATION_FAILED,
                                                "没有可用的微信一次性订阅授权"));
        Instant now = clock.instant();
        grant.claim(now);
        return deliveryRepository.save(
                new WeChatSubscriptionDeliveryEntity(
                        grant.getId(),
                        userId,
                        properties.templateId(),
                        eventType,
                        eventId,
                        title,
                        content,
                        url,
                        now));
    }
}
