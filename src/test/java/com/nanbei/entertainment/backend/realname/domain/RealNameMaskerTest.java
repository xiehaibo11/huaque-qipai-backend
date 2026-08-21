package com.nanbei.entertainment.backend.realname.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RealNameMaskerTest {
    @Test
    void masksChineseNamesKeepingTheFirstCharacter() {
        assertThat(RealNameMasker.maskName("张三")).isEqualTo("张*");
        assertThat(RealNameMasker.maskName("张测试")).isEqualTo("张**");
        assertThat(RealNameMasker.maskName("欧阳测试")).isEqualTo("欧***");
    }

    @Test
    void masksIdCardKeepingFirstAndLastFourDigits() {
        assertThat(RealNameMasker.maskIdCard("110101199001011237"))
                .isEqualTo("1101**********1237");
    }

    @Test
    void toleratesBlankInput() {
        assertThat(RealNameMasker.maskName(null)).isEmpty();
        assertThat(RealNameMasker.maskName("  ")).isEmpty();
        assertThat(RealNameMasker.maskIdCard(null)).isEmpty();
    }
}
