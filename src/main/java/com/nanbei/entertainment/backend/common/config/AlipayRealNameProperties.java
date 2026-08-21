package com.nanbei.entertainment.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("nanbei.alipay-realname")
public record AlipayRealNameProperties(
        boolean enabled,
        String appId,
        String privateKey,
        String alipayPublicKey) {
    public boolean isConfigured() {
        return enabled
                && appId != null
                && !appId.isBlank()
                && privateKey != null
                && !privateKey.isBlank()
                && alipayPublicKey != null
                && !alipayPublicKey.isBlank();
    }
}
