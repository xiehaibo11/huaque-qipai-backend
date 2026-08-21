package com.nanbei.entertainment.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("nanbei.sms")
public record SmsProperties(
        boolean enabled,
        String regionId,
        String signName,
        String templateCode) {
}
