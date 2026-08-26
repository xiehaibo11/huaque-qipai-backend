package com.nanbei.entertainment.backend.wechatsubscription.infrastructure;

public enum WeChatSubscriptionSendStatus {
    SENT,
    RETRYABLE,
    TERMINAL,
    AMBIGUOUS
}
