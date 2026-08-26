package com.nanbei.entertainment.backend.wechatpush.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WeChatPushSignatureTest {
    @Test
    void verifiesDocumentedUrlChallengeVector() {
        WeChatPushSignature signature = new WeChatPushSignature("AAAAA");

        assertThat(
                        signature.matches(
                                "f464b24fc39322e44b38aa78f5edd27bd1441696",
                                "1714036504",
                                "1514711492"))
                .isTrue();
        assertThat(signature.matches("bad", "1714036504", "1514711492"))
                .isFalse();
    }

    @Test
    void verifiesEncryptedMessageSignature() {
        WeChatPushSignature signature = new WeChatPushSignature("token");
        String expected = signature.sign("1700000000", "nonce", "ciphertext");

        assertThat(
                        signature.matchesEncrypted(
                                expected, "1700000000", "nonce", "ciphertext"))
                .isTrue();
        assertThat(
                        signature.matchesEncrypted(
                                expected, "1700000000", "nonce", "tampered"))
                .isFalse();
    }
}
