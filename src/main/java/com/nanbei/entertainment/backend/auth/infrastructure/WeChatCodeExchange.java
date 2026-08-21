package com.nanbei.entertainment.backend.auth.infrastructure;

@FunctionalInterface
interface WeChatCodeExchange {
    WeChatTokenResponse exchange(String code);
}
