package com.nanbei.entertainment.backend.wechatsubscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.config.WeChatSubscriptionProperties;
import com.nanbei.entertainment.backend.wechatsubscription.domain.WeChatSubscriptionDeliveryEntity;
import com.nanbei.entertainment.backend.wechatsubscription.domain.WeChatSubscriptionDeliveryStatus;
import com.nanbei.entertainment.backend.wechatsubscription.domain.WeChatSubscriptionGrantEntity;
import com.nanbei.entertainment.backend.wechatsubscription.domain.WeChatSubscriptionGrantStatus;
import com.nanbei.entertainment.backend.wechatsubscription.infrastructure.WeChatSubscriptionDeliveryRepository;
import com.nanbei.entertainment.backend.wechatsubscription.infrastructure.WeChatSubscriptionGrantRepository;
import com.nanbei.entertainment.backend.wechatsubscription.infrastructure.WeChatSubscriptionSendResult;
import com.nanbei.entertainment.backend.wechatsubscription.infrastructure.WeChatSubscriptionSendStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeChatSubscriptionDeliveryStateServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");

    @Mock WeChatSubscriptionGrantRepository grantRepository;
    @Mock WeChatSubscriptionDeliveryRepository deliveryRepository;

    private WeChatSubscriptionDeliveryStateService service;

    @BeforeEach
    void setUp() {
        service =
                new WeChatSubscriptionDeliveryStateService(
                        grantRepository,
                        deliveryRepository,
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void startNextMarksPersistedDeliverySendingBeforeReturningWork() {
        WeChatSubscriptionGrantEntity grant = claimedGrant();
        WeChatSubscriptionDeliveryEntity delivery = delivery(grant);
        when(deliveryRepository.findNextLocked(NOW))
                .thenReturn(Optional.of(delivery));
        when(grantRepository.findLockedById(grant.getId()))
                .thenReturn(Optional.of(grant));

        Optional<WeChatSubscriptionDeliveryWork> work = service.startNext();

        assertThat(work).isPresent();
        assertThat(work.orElseThrow().openIdSubjectHash())
                .isEqualTo("b".repeat(64));
        assertThat(delivery.getStatus())
                .isEqualTo(WeChatSubscriptionDeliveryStatus.SENDING);
    }

    @Test
    void ambiguousOutcomeTerminatesSameGrantWithoutAllocatingAnother() {
        WeChatSubscriptionGrantEntity grant = claimedGrant();
        WeChatSubscriptionDeliveryEntity delivery = delivery(grant);
        delivery.start(NOW.minusSeconds(1));
        when(deliveryRepository.findLockedById(delivery.getId()))
                .thenReturn(Optional.of(delivery));
        when(grantRepository.findLockedById(grant.getId()))
                .thenReturn(Optional.of(grant));

        service.complete(
                delivery.getId(),
                new WeChatSubscriptionSendResult(
                        WeChatSubscriptionSendStatus.AMBIGUOUS,
                        null,
                        "NETWORK_AMBIGUOUS"));

        assertThat(delivery.getStatus())
                .isEqualTo(WeChatSubscriptionDeliveryStatus.AMBIGUOUS);
        assertThat(grant.getStatus())
                .isEqualTo(WeChatSubscriptionGrantStatus.TERMINAL);
    }

    private static WeChatSubscriptionGrantEntity claimedGrant() {
        WeChatSubscriptionGrantEntity grant =
                new WeChatSubscriptionGrantEntity(
                        UUID.randomUUID(),
                        WeChatSubscriptionProperties.TEMPLATE_ID,
                        1000,
                        "a".repeat(64),
                        "b".repeat(64),
                        NOW.plusSeconds(600),
                        NOW.minusSeconds(30));
        grant.accept(NOW.minusSeconds(20));
        grant.claim(NOW.minusSeconds(10));
        return grant;
    }

    private static WeChatSubscriptionDeliveryEntity delivery(
            WeChatSubscriptionGrantEntity grant) {
        return new WeChatSubscriptionDeliveryEntity(
                grant.getId(),
                grant.getUserId(),
                grant.getTemplateId(),
                "SYSTEM_EVENT",
                "event-1",
                "系统通知",
                "真实业务事件已完成",
                null,
                NOW.minusSeconds(5));
    }
}
