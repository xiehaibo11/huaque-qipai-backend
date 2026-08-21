package com.nanbei.entertainment.backend.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecureOtpCodeGeneratorTest {
    @Test
    void generatesVariableSixDigitCodes() {
        SecureOtpCodeGenerator generator = new SecureOtpCodeGenerator();
        Set<String> codes = new HashSet<>();

        for (int index = 0; index < 100; index++) {
            String code = generator.generate();
            assertThat(code).matches("\\d{6}");
            codes.add(code);
        }

        assertThat(codes).hasSizeGreaterThan(1);
    }
}
