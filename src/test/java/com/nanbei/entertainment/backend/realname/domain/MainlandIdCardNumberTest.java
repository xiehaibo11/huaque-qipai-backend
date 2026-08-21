package com.nanbei.entertainment.backend.realname.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MainlandIdCardNumberTest {
    // 110101199001011237：权重 7,9,10,5,8,4,2,1,6,3,7,9,10,5,8,4,2
    // 加权和 126，126 % 11 = 5，余数映射 10X98765432 → 校验位 7。
    private static final String VALID = "110101199001011237";

    @Test
    void parsesAValidIdCardNumber() {
        MainlandIdCardNumber idCard = MainlandIdCardNumber.parse(VALID);

        assertThat(idCard.value()).isEqualTo(VALID);
        assertThat(idCard.birthDate())
                .isEqualTo(LocalDate.of(1990, 1, 1));
    }

    @Test
    void normalizesLowercaseCheckDigitX() {
        MainlandIdCardNumber idCard =
                MainlandIdCardNumber.parse("11010120000229123x");

        assertThat(idCard.value()).isEqualTo("11010120000229123X");
        assertThat(idCard.birthDate())
                .isEqualTo(LocalDate.of(2000, 2, 29));
    }

    @Test
    void rejectsAWrongCheckDigit() {
        assertThatThrownBy(
                        () ->
                                MainlandIdCardNumber.parse(
                                        "110101199001011238"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.REALNAME_INVALID_FORMAT);
    }

    @Test
    void acceptsLeapDayOfALeapYear() {
        MainlandIdCardNumber idCard =
                MainlandIdCardNumber.parse("11010120000229123X");

        assertThat(idCard.birthDate())
                .isEqualTo(LocalDate.of(2000, 2, 29));
    }

    @Test
    void rejectsLeapDayOfANonLeapYear() {
        // 校验位 3 对该前十七位合法，但 1900 年不是闰年。
        assertThatThrownBy(
                        () ->
                                MainlandIdCardNumber.parse(
                                        "110101190002291233"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsAnInvalidMonthDayCombination() {
        // 校验位 5 对该前十七位合法，但四月没有 31 日。
        assertThatThrownBy(
                        () ->
                                MainlandIdCardNumber.parse(
                                        "110101199004311235"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsALeadingZero() {
        assertThatThrownBy(
                        () ->
                                MainlandIdCardNumber.parse(
                                        "010101199001011237"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsShortOrNullValues() {
        assertThatThrownBy(() -> MainlandIdCardNumber.parse("110101"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> MainlandIdCardNumber.parse(null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void evaluatesAdulthoodByExactBirthdayBoundary() {
        MainlandIdCardNumber idCard = MainlandIdCardNumber.parse(VALID);

        assertThat(idCard.isAdultOn(LocalDate.of(2008, 1, 1))).isTrue();
        assertThat(idCard.isAdultOn(LocalDate.of(2007, 12, 31)))
                .isFalse();
        assertThat(idCard.isAdult()).isTrue();
    }

    @Test
    void rejectsUnderageCardHoldersByCurrentDate() {
        MainlandIdCardNumber underage =
                MainlandIdCardNumber.parse("110101201506011232");

        assertThat(underage.isAdult()).isFalse();
    }
}
