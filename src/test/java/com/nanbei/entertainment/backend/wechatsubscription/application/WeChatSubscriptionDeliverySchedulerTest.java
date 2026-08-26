package com.nanbei.entertainment.backend.wechatsubscription.application;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nanbei.entertainment.backend.common.config.WeChatSubscriptionProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeChatSubscriptionDeliverySchedulerTest {
    @Mock WeChatSubscriptionDeliveryWorker worker;

    @Test
    void disabledConfigurationDoesNotProcessQueue() {
        var scheduler =
                new WeChatSubscriptionDeliveryScheduler(
                        worker,
                        new WeChatSubscriptionProperties(
                                false,
                                WeChatSubscriptionProperties.TEMPLATE_ID,
                                1000));

        scheduler.processNext();

        verify(worker, never()).processNext();
    }

    @Test
    void configuredSchedulerProcessesAtMostOneDeliveryPerTick() {
        var scheduler =
                new WeChatSubscriptionDeliveryScheduler(
                        worker,
                        new WeChatSubscriptionProperties(
                                true,
                                WeChatSubscriptionProperties.TEMPLATE_ID,
                                1000));

        scheduler.processNext();

        verify(worker).processNext();
    }
}
