package com.nanbei.entertainment.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

class MainlandPhoneNumberTest {
    @Test
    void normalizesMainlandPhoneNumbers() {
        assertThat(MainlandPhoneNumber.parse("13800138000").value())
                .isEqualTo("13800138000");
        assertThat(MainlandPhoneNumber.parse("+86 138-0013-8000").value())
                .isEqualTo("13800138000");
        assertThat(MainlandPhoneNumber.parse("0086 139 0013 9000").value())
                .isEqualTo("13900139000");
    }

    @Test
    void rejectsNonMainlandPhoneNumbers() {
        assertThatThrownBy(() -> MainlandPhoneNumber.parse("12345678"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.code())
                                        .isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> MainlandPhoneNumber.parse("+85212345678"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> MainlandPhoneNumber.parse("1380013800A"))
                .isInstanceOf(ApiException.class);
    }
}
