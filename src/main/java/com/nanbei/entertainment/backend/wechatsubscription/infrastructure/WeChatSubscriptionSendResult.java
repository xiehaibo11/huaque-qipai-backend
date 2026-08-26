package com.nanbei.entertainment.backend.wechatsubscription.infrastructure;

public record WeChatSubscriptionSendResult(
        WeChatSubscriptionSendStatus status,
        Integer providerCode,
        String failureClass) {
    static WeChatSubscriptionSendResult sent() {
        return new WeChatSubscriptionSendResult(
                WeChatSubscriptionSendStatus.SENT, 0, null);
    }

    static WeChatSubscriptionSendResult retryable(String failureClass) {
        return new WeChatSubscriptionSendResult(
                WeChatSubscriptionSendStatus.RETRYABLE, null, failureClass);
    }

    static WeChatSubscriptionSendResult terminal(
            Integer providerCode, String failureClass) {
        return new WeChatSubscriptionSendResult(
                WeChatSubscriptionSendStatus.TERMINAL,
                providerCode,
                failureClass);
    }

    static WeChatSubscriptionSendResult ambiguous() {
        return new WeChatSubscriptionSendResult(
                WeChatSubscriptionSendStatus.AMBIGUOUS,
                null,
                "NETWORK_AMBIGUOUS");
    }
}
