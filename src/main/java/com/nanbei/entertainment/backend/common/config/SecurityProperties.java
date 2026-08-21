package com.nanbei.entertainment.backend.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("nanbei.security")
public record SecurityProperties(
        String jwtSecret, Duration accessTokenTtl, Duration refreshTokenTtl) {}
