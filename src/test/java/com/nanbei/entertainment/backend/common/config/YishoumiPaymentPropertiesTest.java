package com.nanbei.entertainment.backend.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class YishoumiPaymentPropertiesTest {
    @Test
    void isConfiguredOnlyWithEnabledSecretsAndHttpsEndpoints() {
        assertThat(properties(true, "app-id", "app-secret", "https").configured())
                .isTrue();
        assertThat(properties(false, "app-id", "app-secret", "https").configured())
                .isFalse();
        assertThat(properties(true, "", "app-secret", "https").configured())
                .isFalse();
        assertThat(properties(true, "app-id", "", "https").configured())
                .isFalse();
        assertThat(properties(true, "app-id", "app-secret", "http").configured())
                .isFalse();
    }

    private static YishoumiPaymentProperties properties(
            boolean enabled,
            String appId,
            String appSecret,
            String scheme) {
        return new YishoumiPaymentProperties(
                enabled,
                appId,
                appSecret,
                URI.create(scheme + "://payments.example/payment"),
                URI.create(scheme + "://api.example/webhook"),
                URI.create(scheme + "://www.example/payment/result"),
                URI.create(scheme + "://www.example/payment/result"),
                Duration.ofSeconds(3),
                Duration.ofSeconds(5));
    }
}
