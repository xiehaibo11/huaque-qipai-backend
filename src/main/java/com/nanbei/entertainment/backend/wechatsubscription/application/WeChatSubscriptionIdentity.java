package com.nanbei.entertainment.backend.wechatsubscription.application;

public record WeChatSubscriptionIdentity(
        String subject, String openId, String subjectHash) {}
