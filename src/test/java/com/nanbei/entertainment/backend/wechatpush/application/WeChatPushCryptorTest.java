package com.nanbei.entertainment.backend.wechatpush.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WeChatPushCryptorTest {
    private static final String APP_ID = "wx-test-app";
    private static final String AES_KEY = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";

    @Test
    void decryptsSafeModePayloadAndValidatesAppId() throws Exception {
        String message = "{\"Event\":\"user_info_modified\"}";
        WeChatPushCryptor cryptor = new WeChatPushCryptor(AES_KEY, APP_ID);

        assertThat(cryptor.decrypt(encrypt(message, APP_ID))).isEqualTo(message);
        assertThatThrownBy(() -> cryptor.decrypt(encrypt(message, "wrong-app")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTamperedCiphertext() throws Exception {
        byte[] encrypted = Base64.getDecoder().decode(encrypt("{}", APP_ID));
        encrypted[encrypted.length - 1] ^= 1;

        assertThatThrownBy(
                        () ->
                                new WeChatPushCryptor(AES_KEY, APP_ID)
                                        .decrypt(Base64.getEncoder().encodeToString(encrypted)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String encrypt(String message, String appId) throws Exception {
        byte[] key = Base64.getDecoder().decode(AES_KEY + "=");
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] appIdBytes = appId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer plain =
                ByteBuffer.allocate(16 + 4 + messageBytes.length + appIdBytes.length + 32);
        plain.put(new byte[16]);
        plain.putInt(messageBytes.length);
        plain.put(messageBytes);
        plain.put(appIdBytes);
        int used = plain.position();
        int padding = 32 - used % 32;
        byte[] padded = Arrays.copyOf(plain.array(), used + padding);
        Arrays.fill(padded, used, padded.length, (byte) padding);
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(key, "AES"),
                new IvParameterSpec(key, 0, 16));
        return Base64.getEncoder().encodeToString(cipher.doFinal(padded));
    }
}
