package com.nanbei.entertainment.backend.wechatsubscription.application;

import com.nanbei.entertainment.backend.common.config.WeChatSubscriptionProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WeChatSubscriptionDeliveryScheduler {
    private final WeChatSubscriptionDeliveryWorker worker;
    private final WeChatSubscriptionProperties properties;

    public WeChatSubscriptionDeliveryScheduler(
            WeChatSubscriptionDeliveryWorker worker,
            WeChatSubscriptionProperties properties) {
        this.worker = worker;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString =
                    "${nanbei.wechat.subscription.worker-delay:PT1S}",
            initialDelayString =
                    "${nanbei.wechat.subscription.worker-initial-delay:PT1S}")
    void processNext() {
        if (properties.configured()) {
            worker.processNext();
        }
    }
}
