package com.nanbei.entertainment.backend.wechatsubscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.config.WeChatSubscriptionProperties;
import com.nanbei.entertainment.backend.wechatsubscription.domain.WeChatSubscriptionDeliveryEntity;
import com.nanbei.entertainment.backend.wechatsubscription.domain.WeChatSubscriptionDeliveryStatus;
import com.nanbei.entertainment.backend.wechatsubscription.domain.WeChatSubscriptionGrantEntity;
import com.nanbei.entertainment.backend.wechatsubscription.domain.WeChatSubscriptionGrantStatus;
import com.nanbei.entertainment.backend.wechatsubscription.infrastructure.WeChatSubscriptionDeliveryRepository;
import com.nanbei.entertainment.backend.wechatsubscription.infrastructure.WeChatSubscriptionGrantRepository;
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
class WeChatSubscriptionEnqueueServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock WeChatSubscriptionGrantRepository grantRepository;
    @Mock WeChatSubscriptionDeliveryRepository deliveryRepository;

    private WeChatSubscriptionEnqueueService service;

    @BeforeEach
    void setUp() {
        service =
                new WeChatSubscriptionEnqueueService(
                        grantRepository,
                        deliveryRepository,
                        new WeChatSubscriptionProperties(
                                true,
                                WeChatSubscriptionProperties.TEMPLATE_ID,
                                1000),
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void enqueueClaimsOneGrantAndCreatesPendingDelivery() {
        WeChatSubscriptionGrantEntity grant = availableGrant();
        when(deliveryRepository.findByUserIdAndTemplateIdAndEventTypeAndEventId(
                        USER_ID,
                        WeChatSubscriptionProperties.TEMPLATE_ID,
                        "SYSTEM_EVENT",
                        "event-1"))
                .thenReturn(Optional.empty());
        when(grantRepository.findOldestAvailableLocked(
                        USER_ID,
                        WeChatSubscriptionProperties.TEMPLATE_ID,
                        1000))
                .thenReturn(Optional.of(grant));
        when(deliveryRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WeChatSubscriptionDeliveryEntity delivery =
                service.enqueue(
                        USER_ID,
                        "SYSTEM_EVENT",
                        "event-1",
                        "系统通知",
                        "真实业务事件已完成",
                        null);

        assertThat(grant.getStatus()).isEqualTo(WeChatSubscriptionGrantStatus.CLAIMED);
        assertThat(delivery.getGrantId()).isEqualTo(grant.getId());
        assertThat(delivery.getStatus())
                .isEqualTo(WeChatSubscriptionDeliveryStatus.PENDING);
        verify(deliveryRepository)
                .acquireEventLock(
                        USER_ID
                                + ":"
                                + WeChatSubscriptionProperties.TEMPLATE_ID
                                + ":SYSTEM_EVENT:event-1");
    }

    @Test
    void duplicateBusinessEventReturnsExistingDeliveryWithoutAnotherGrant() {
        WeChatSubscriptionDeliveryEntity existing =
                new WeChatSubscriptionDeliveryEntity(
                        UUID.randomUUID(),
                        USER_ID,
                        WeChatSubscriptionProperties.TEMPLATE_ID,
                        "SYSTEM_EVENT",
                        "event-1",
                        "系统通知",
                        "真实业务事件已完成",
                        null,
                        NOW);
        when(deliveryRepository.findByUserIdAndTemplateIdAndEventTypeAndEventId(
                        USER_ID,
                        WeChatSubscriptionProperties.TEMPLATE_ID,
                        "SYSTEM_EVENT",
                        "event-1"))
                .thenReturn(Optional.of(existing));

        WeChatSubscriptionDeliveryEntity replay =
                service.enqueue(
                        USER_ID,
                        "SYSTEM_EVENT",
                        "event-1",
                        "系统通知",
                        "真实业务事件已完成",
                        null);

        assertThat(replay).isSameAs(existing);
        verify(grantRepository, never())
                .findOldestAvailableLocked(
                        USER_ID,
                        WeChatSubscriptionProperties.TEMPLATE_ID,
                        1000);
    }

    private static WeChatSubscriptionGrantEntity availableGrant() {
        WeChatSubscriptionGrantEntity grant =
                new WeChatSubscriptionGrantEntity(
                        USER_ID,
                        WeChatSubscriptionProperties.TEMPLATE_ID,
                        1000,
                        "a".repeat(64),
                        "b".repeat(64),
                        NOW.plusSeconds(600),
                        NOW.minusSeconds(10));
        grant.accept(NOW.minusSeconds(5));
        return grant;
    }
}
