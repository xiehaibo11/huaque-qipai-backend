package com.nanbei.entertainment.backend.wechatsubscription.infrastructure;

public record WeChatSubscriptionMessage(
        String openId,
        String templateId,
        int scene,
        String title,
        String content,
        String url) {}
