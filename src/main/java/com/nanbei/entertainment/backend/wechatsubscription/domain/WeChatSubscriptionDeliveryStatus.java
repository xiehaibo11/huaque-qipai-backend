package com.nanbei.entertainment.backend.wechatsubscription.domain;

public enum WeChatSubscriptionDeliveryStatus {
    PENDING,
    SENDING,
    RETRYABLE,
    SENT,
    TERMINAL,
    AMBIGUOUS
}
