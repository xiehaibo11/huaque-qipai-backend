package com.nanbei.entertainment.backend.common.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("nanbei.payment.yishoumi")
public record YishoumiPaymentProperties(
        boolean enabled,
        String appId,
        String appSecret,
        URI paymentUrl,
        URI notifyUrl,
        URI callbackUrl,
        URI nopayUrl,
        Duration connectTimeout,
        Duration readTimeout) {
    public boolean configured() {
        return enabled
                && hasText(appId)
                && hasText(appSecret)
                && isHttps(paymentUrl)
                && isHttps(notifyUrl)
                && isHttps(callbackUrl)
                && isHttps(nopayUrl)
                && positive(connectTimeout)
                && positive(readTimeout);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isHttps(URI uri) {
        return uri != null
                && "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null
                && !uri.getHost().isBlank();
    }

    private static boolean positive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
