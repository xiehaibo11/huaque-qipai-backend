package com.nanbei.entertainment.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("nanbei.payment")
public record PaymentProperties(String mockWebhookSecret) {}
