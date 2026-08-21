package com.nanbei.entertainment.backend.payment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class YishoumiSignerTest {
    private final YishoumiSigner signer = new YishoumiSigner();

    @Test
    void signsNonEmptyFieldsInAsciiKeyOrder() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("total", "26800");
        fields.put("appid", "app-1");
        fields.put("empty", "");

        assertThat(signer.sign(fields, "secret"))
                .isEqualTo(
                        "9a5b736e9cd8fd96df9b90acd1e371981bb834ca7a9212109b7aa6ec8b7ea651");
    }

    @Test
    void excludesSignAndHashDuringVerification() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("total", "26800");
        fields.put("sign", "untrusted-signature");
        fields.put("hash", "legacy-signature-name");
        fields.put("appid", "app-1");

        String expected =
                "9a5b736e9cd8fd96df9b90acd1e371981bb834ca7a9212109b7aa6ec8b7ea651";
        assertThat(signer.verify(fields, "secret", expected)).isTrue();
        assertThat(signer.verify(fields, "secret", expected + "0")).isFalse();
    }
}
