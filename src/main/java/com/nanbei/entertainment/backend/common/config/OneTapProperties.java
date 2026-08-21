package com.nanbei.entertainment.backend.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("nanbei.one-tap")
public record OneTapProperties(
        boolean enabled,
        String regionId,
        String endpoint,
        Duration connectTimeout,
        Duration readTimeout) {}
