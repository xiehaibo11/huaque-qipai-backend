package com.nanbei.entertainment.backend.wechatsubscription.application;

import com.nanbei.entertainment.backend.wechatsubscription.domain.WeChatSubscriptionDeliveryEntity;
import com.nanbei.entertainment.backend.wechatsubscription.domain.WeChatSubscriptionGrantEntity;
import com.nanbei.entertainment.backend.wechatsubscription.domain.WeChatSubscriptionGrantStatus;
import com.nanbei.entertainment.backend.wechatsubscription.infrastructure.WeChatSubscriptionDeliveryRepository;
import com.nanbei.entertainment.backend.wechatsubscription.infrastructure.WeChatSubscriptionGrantRepository;
import com.nanbei.entertainment.backend.wechatsubscription.infrastructure.WeChatSubscriptionSendResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeChatSubscriptionDeliveryStateService {
    private static final Duration RETRY_DELAY = Duration.ofMinutes(1);

    private final WeChatSubscriptionGrantRepository grantRepository;
    private final WeChatSubscriptionDeliveryRepository deliveryRepository;
    private final Clock clock;

    @Autowired
    public WeChatSubscriptionDeliveryStateService(
            WeChatSubscriptionGrantRepository grantRepository,
            WeChatSubscriptionDeliveryRepository deliveryRepository) {
        this(grantRepository, deliveryRepository, Clock.systemUTC());
    }

    WeChatSubscriptionDeliveryStateService(
            WeChatSubscriptionGrantRepository grantRepository,
            WeChatSubscriptionDeliveryRepository deliveryRepository,
            Clock clock) {
        this.grantRepository = grantRepository;
        this.deliveryRepository = deliveryRepository;
        this.clock = clock;
    }

    @Transactional
    public Optional<WeChatSubscriptionDeliveryWork> startNext() {
        Instant now = clock.instant();
        Optional<WeChatSubscriptionDeliveryEntity> next =
                deliveryRepository.findNextLocked(now);
        if (next.isEmpty()) {
            return Optional.empty();
        }
        WeChatSubscriptionDeliveryEntity delivery = next.get();
        delivery.start(now);
        Optional<WeChatSubscriptionGrantEntity> lockedGrant =
                grantRepository.findLockedById(delivery.getGrantId());
        if (lockedGrant.isEmpty()
                || lockedGrant.get().getStatus()
                        != WeChatSubscriptionGrantStatus.CLAIMED) {
            delivery.terminal(null, "GRANT_UNAVAILABLE", now);
            return Optional.empty();
        }
        WeChatSubscriptionGrantEntity grant = lockedGrant.get();
        return Optional.of(
                new WeChatSubscriptionDeliveryWork(
                        delivery.getId(),
                        grant.getId(),
                        delivery.getUserId(),
                        delivery.getTemplateId(),
                        grant.getScene(),
                        grant.getOpenIdSubjectHash(),
                        delivery.getTitle(),
                        delivery.getContent(),
                        delivery.getTargetUrl()));
    }

    @Transactional
    public void complete(UUID deliveryId, WeChatSubscriptionSendResult result) {
        WeChatSubscriptionDeliveryEntity delivery = requireDelivery(deliveryId);
        WeChatSubscriptionGrantEntity grant = requireGrant(delivery.getGrantId());
        Instant now = clock.instant();
        switch (result.status()) {
            case SENT -> {
                delivery.sent(now);
                grant.sent(now);
            }
            case RETRYABLE ->
                    delivery.retryable(
                            result.providerCode(),
                            result.failureClass(),
                            now.plus(RETRY_DELAY));
            case TERMINAL -> {
                delivery.terminal(
                        result.providerCode(), result.failureClass(), now);
                grant.terminal(now);
            }
            case AMBIGUOUS -> {
                delivery.ambiguous(now);
                grant.terminal(now);
            }
        }
    }

    @Transactional
    public void invalidate(UUID deliveryId) {
        WeChatSubscriptionDeliveryEntity delivery = requireDelivery(deliveryId);
        WeChatSubscriptionGrantEntity grant = requireGrant(delivery.getGrantId());
        Instant now = clock.instant();
        delivery.terminal(null, "IDENTITY_INVALIDATED", now);
        grant.invalidate(now);
    }

    private WeChatSubscriptionDeliveryEntity requireDelivery(UUID deliveryId) {
        return deliveryRepository
                .findLockedById(deliveryId)
                .orElseThrow(() -> new IllegalStateException("delivery is missing"));
    }

    private WeChatSubscriptionGrantEntity requireGrant(UUID grantId) {
        return grantRepository
                .findLockedById(grantId)
                .orElseThrow(() -> new IllegalStateException("grant is missing"));
    }
}
