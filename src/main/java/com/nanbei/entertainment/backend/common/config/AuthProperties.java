package com.nanbei.entertainment.backend.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("nanbei.auth")
public record AuthProperties(String localOtp, Duration otpTtl, int maxAttempts) {}
