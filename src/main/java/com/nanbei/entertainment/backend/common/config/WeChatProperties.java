package com.nanbei.entertainment.backend.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("nanbei.wechat")
public record WeChatProperties(
        boolean enabled,
        String appId,
        String appSecret,
        Duration connectTimeout,
        Duration readTimeout) {
    public boolean isConfigured() {
        return enabled
                && appId != null
                && !appId.isBlank()
                && appSecret != null
                && !appSecret.isBlank();
    }
}
