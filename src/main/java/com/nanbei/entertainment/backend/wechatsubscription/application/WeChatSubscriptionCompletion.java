package com.nanbei.entertainment.backend.wechatsubscription.application;

public record WeChatSubscriptionCompletion(
        int errCode,
        String action,
        String templateId,
        int scene,
        String reserved,
        String openId,
        String transaction) {}
