package com.nanbei.entertainment.backend.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanbei.entertainment.backend.auth.application.ExternalIdentityVerifier;
import com.nanbei.entertainment.backend.auth.application.OneTapMobileExchange;
import com.nanbei.entertainment.backend.common.config.AliyunOneTapConfiguration;
import com.nanbei.entertainment.backend.common.config.OneTapProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class OneTapProductionWiringTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withBean(
                            OneTapProperties.class,
                            OneTapProductionWiringTest::properties);

    @Test
    void disabledModeHasExactlyOneUnavailableExchangeAndVerifier() {
        contextRunner
                .withPropertyValues(
                        "nanbei.one-tap.enabled=false")
                .withUserConfiguration(
                        DisabledConfiguration.class)
                .run(
                        context -> {
                            assertThat(context)
                                    .hasSingleBean(
                                            OneTapMobileExchange.class)
                                    .hasSingleBean(
                                            ExternalIdentityVerifier.class)
                                    .hasSingleBean(
                                            UnavailableOneTapMobileExchange.class);
                        });
    }

    @Test
    void enabledModeHasExactlyOneTypedExchangeAndVerifier() {
        contextRunner
                .withPropertyValues(
                        "nanbei.one-tap.enabled=true")
                .withUserConfiguration(
                        EnabledConfiguration.class)
                .run(
                        context -> {
                            assertThat(context)
                                    .hasSingleBean(
                                            OneTapMobileExchange.class)
                                    .hasSingleBean(
                                            ExternalIdentityVerifier.class)
                                    .hasSingleBean(
                                            AliyunOneTapMobileExchange.class)
                                    .hasSingleBean(
                                            AliyunDypnsClient.class);
                        });
    }

    private static OneTapProperties properties() {
        return new OneTapProperties(
                false,
                "cn-hangzhou",
                "dypnsapi.aliyuncs.com",
                Duration.ofSeconds(3),
                Duration.ofSeconds(5));
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
        OneTapExternalIdentityVerifier.class,
        UnavailableOneTapMobileExchange.class
    })
    static class DisabledConfiguration {}

    @Configuration(proxyBeanMethods = false)
    @Import({
        OneTapExternalIdentityVerifier.class,
        AliyunOneTapConfiguration.class
    })
    static class EnabledConfiguration {}
}
