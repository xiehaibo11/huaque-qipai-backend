package com.nanbei.entertainment.backend.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nanbei.entertainment.backend.auth.application.OneTapMobileExchange;
import com.nanbei.entertainment.backend.common.config.OneTapProperties;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class UnavailableOneTapMobileExchangeTest {
    @Test
    void rejectsExchangeWhenServiceIsDisabled() {
        OneTapProperties properties =
                new OneTapProperties(
                        false,
                        "cn-hangzhou",
                        "dypnsapi.aliyuncs.com",
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(5));
        OneTapMobileExchange exchange =
                new UnavailableOneTapMobileExchange(properties);

        assertThatThrownBy(() -> exchange.exchange("token", "trace"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.code())
                                    .isEqualTo(
                                            ErrorCode
                                                    .AUTH_PROVIDER_UNAVAILABLE);
                            assertThat(exception)
                                    .hasMessageContaining("尚未启用");
                        });
    }

    @Test
    void rejectsExchangeWhenEnabledAdapterIsNotInstalled() {
        OneTapProperties properties =
                new OneTapProperties(
                        true,
                        "cn-hangzhou",
                        "dypnsapi.aliyuncs.com",
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(5));
        OneTapMobileExchange exchange =
                new UnavailableOneTapMobileExchange(properties);

        assertThatThrownBy(() -> exchange.exchange("token", "trace"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.code())
                                    .isEqualTo(
                                            ErrorCode
                                                    .AUTH_PROVIDER_UNAVAILABLE);
                            assertThat(exception)
                                    .hasMessageContaining("适配器");
                        });
    }
}
