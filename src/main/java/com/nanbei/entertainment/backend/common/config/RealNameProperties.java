package com.nanbei.entertainment.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("nanbei.realname")
public record RealNameProperties(
        boolean enabled,
        String endpoint,
        String appCode,
        String hmacSecret,
        int maxFailedAttempts) {
    public boolean isConfigured() {
        return enabled
                && endpoint != null
                && !endpoint.isBlank()
                && appCode != null
                && !appCode.isBlank()
                && hmacSecret != null
                && !hmacSecret.isBlank();
    }
}
