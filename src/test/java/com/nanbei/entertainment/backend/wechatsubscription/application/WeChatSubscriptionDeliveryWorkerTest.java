package com.nanbei.entertainment.backend.wechatsubscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.config.WeChatSubscriptionProperties;
import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.wechatsubscription.infrastructure.WeChatSubscriptionSendResult;
import com.nanbei.entertainment.backend.wechatsubscription.infrastructure.WeChatSubscriptionSendStatus;
import com.nanbei.entertainment.backend.wechatsubscription.infrastructure.WeChatSubscriptionSender;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeChatSubscriptionDeliveryWorkerTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID DELIVERY_ID = UUID.randomUUID();
    private static final UUID GRANT_ID = UUID.randomUUID();
    private static final String SUBJECT = "appid:wx-test:openid:openid-1";

    @Mock WeChatSubscriptionDeliveryStateService stateService;
    @Mock WeChatSubscriptionIdentityService identityService;
    @Mock WeChatSubscriptionSender sender;

    private CryptoService cryptoService;
    private WeChatSubscriptionDeliveryWorker worker;

    @BeforeEach
    void setUp() {
        cryptoService = new CryptoService();
        worker =
                new WeChatSubscriptionDeliveryWorker(
                        stateService, identityService, sender, cryptoService);
    }

    @Test
    void sendsOnlyAfterCurrentOpenIdMatchesOriginalGrant() {
        WeChatSubscriptionDeliveryWork work = work(cryptoService.sha256(SUBJECT));
        WeChatSubscriptionSendResult sent =
                new WeChatSubscriptionSendResult(
                        WeChatSubscriptionSendStatus.SENT, 0, null);
        when(stateService.startNext()).thenReturn(Optional.of(work));
        when(identityService.requireCurrent(USER_ID))
                .thenReturn(
                        new WeChatSubscriptionIdentity(
                                SUBJECT,
                                "openid-1",
                                cryptoService.sha256(SUBJECT)));
        when(sender.send(any())).thenReturn(sent);

        assertThat(worker.processNext()).isTrue();

        verify(stateService).complete(DELIVERY_ID, sent);
    }

    @Test
    void changedOrRevokedOpenIdInvalidatesWithoutSending() {
        when(stateService.startNext())
                .thenReturn(Optional.of(work("a".repeat(64))));
        when(identityService.requireCurrent(USER_ID))
                .thenReturn(
                        new WeChatSubscriptionIdentity(
                                SUBJECT,
                                "openid-1",
                                cryptoService.sha256(SUBJECT)));

        assertThat(worker.processNext()).isTrue();

        verify(stateService).invalidate(DELIVERY_ID);
        verify(sender, never()).send(any());
    }

    @Test
    void noEnqueuedDeliveryPerformsNoWork() {
        when(stateService.startNext()).thenReturn(Optional.empty());

        assertThat(worker.processNext()).isFalse();

        verify(sender, never()).send(any());
    }

    private static WeChatSubscriptionDeliveryWork work(String subjectHash) {
        return new WeChatSubscriptionDeliveryWork(
                DELIVERY_ID,
                GRANT_ID,
                USER_ID,
                WeChatSubscriptionProperties.TEMPLATE_ID,
                1000,
                subjectHash,
                "系统通知",
                "真实业务事件已完成",
                null);
    }
}
