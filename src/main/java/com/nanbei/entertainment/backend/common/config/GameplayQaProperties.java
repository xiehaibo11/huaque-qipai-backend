package com.nanbei.entertainment.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("nanbei.gameplay.qa")
public record GameplayQaProperties(boolean enabled) {}
