package com.nanbei.entertainment.backend.wechatsubscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WeChatSubscriptionDomainTest {
    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");

    @Test
    void pendingGrantCanBeAcceptedOnlyOnceAndThenClaimed() {
        WeChatSubscriptionGrantEntity grant = pendingGrant();

        grant.accept(NOW.plusSeconds(1));
        grant.accept(NOW.plusSeconds(2));
        grant.claim(NOW.plusSeconds(3));

        assertThat(grant.getStatus()).isEqualTo(WeChatSubscriptionGrantStatus.CLAIMED);
        assertThat(grant.getConfirmedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThatThrownBy(() -> grant.claim(NOW.plusSeconds(4)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancellationAndInvalidationNeverCreateAvailableGrant() {
        WeChatSubscriptionGrantEntity cancelled = pendingGrant();
        cancelled.cancel(NOW.plusSeconds(1));
        cancelled.accept(NOW.plusSeconds(2));

        WeChatSubscriptionGrantEntity invalidated = pendingGrant();
        invalidated.invalidate(NOW.plusSeconds(1));

        assertThat(cancelled.getStatus())
                .isEqualTo(WeChatSubscriptionGrantStatus.CANCELLED);
        assertThat(invalidated.getStatus())
                .isEqualTo(WeChatSubscriptionGrantStatus.INVALIDATED);
    }

    @Test
    void deliveryRejectsWechatTemplateTextOutsideRealLimits() {
        UUID userId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                new WeChatSubscriptionDeliveryEntity(
                                        grantId,
                                        userId,
                                        "template",
                                        "EVENT",
                                        "event-1",
                                        "标".repeat(16),
                                        "content",
                                        null,
                                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new WeChatSubscriptionDeliveryEntity(
                                        grantId,
                                        userId,
                                        "template",
                                        "EVENT",
                                        "event-1",
                                        "title",
                                        "x".repeat(201),
                                        null,
                                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static WeChatSubscriptionGrantEntity pendingGrant() {
        return new WeChatSubscriptionGrantEntity(
                UUID.randomUUID(),
                "template",
                1000,
                "a".repeat(64),
                "b".repeat(64),
                NOW.plusSeconds(600),
                NOW);
    }
}
