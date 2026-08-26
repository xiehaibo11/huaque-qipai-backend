package com.nanbei.entertainment.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("nanbei.wechat.push")
public record WeChatPushProperties(
        boolean enabled, String token, String encodingAesKey) {
    public boolean isConfigured() {
        return enabled
                && token != null
                && !token.isBlank()
                && encodingAesKey != null
                && encodingAesKey.length() == 43;
    }
}
