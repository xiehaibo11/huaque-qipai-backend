package com.nanbei.entertainment.backend.wechatsubscription.infrastructure;

final class WeChatSubscriptionProviderException extends RuntimeException {
    WeChatSubscriptionProviderException(String message) {
        super(message);
    }

    WeChatSubscriptionProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
