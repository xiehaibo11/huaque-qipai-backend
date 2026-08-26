package com.nanbei.entertainment.backend.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GoldRoomPropertiesTest {

    @Test
    void fallsBackToTwoMinutesWhenUnconfiguredOrInvalid() {
        assertThat(new GoldRoomProperties(null).matchTimeout())
                .isEqualTo(Duration.ofSeconds(120));
        assertThat(new GoldRoomProperties(Duration.ZERO).matchTimeout())
                .isEqualTo(Duration.ofSeconds(120));
        assertThat(new GoldRoomProperties(Duration.ofSeconds(-5)).matchTimeout())
                .isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void keepsTheConfiguredTimeout() {
        assertThat(new GoldRoomProperties(Duration.ofSeconds(30)).matchTimeout())
                .isEqualTo(Duration.ofSeconds(30));
    }
}
