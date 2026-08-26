package com.nanbei.entertainment.backend.wechatsubscription.application;

import com.nanbei.entertainment.backend.wechatsubscription.infrastructure.WeChatSubscriptionMessage;
import java.util.UUID;

public record WeChatSubscriptionDeliveryWork(
        UUID deliveryId,
        UUID grantId,
        UUID userId,
        String templateId,
        int scene,
        String openIdSubjectHash,
        String title,
        String content,
        String url) {
    WeChatSubscriptionMessage message(String openId) {
        return new WeChatSubscriptionMessage(
                openId, templateId, scene, title, content, url);
    }
}
