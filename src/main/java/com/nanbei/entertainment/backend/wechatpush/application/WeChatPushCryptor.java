package com.nanbei.entertainment.backend.wechatpush.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class WeChatPushCryptor {
    private static final int RANDOM_PREFIX_BYTES = 16;
    private static final int LENGTH_BYTES = 4;
    private static final int PKCS7_BLOCK_SIZE = 32;

    private final byte[] aesKey;
    private final byte[] expectedAppId;

    public WeChatPushCryptor(String encodingAesKey, String appId) {
        if (encodingAesKey == null || encodingAesKey.length() != 43) {
            throw new IllegalArgumentException("EncodingAESKey must contain 43 characters");
        }
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("WeChat AppID is required");
        }
        try {
            aesKey = Base64.getDecoder().decode(encodingAesKey + "=");
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("EncodingAESKey is invalid", error);
        }
        if (aesKey.length != 32) {
            throw new IllegalArgumentException("EncodingAESKey is invalid");
        }
        expectedAppId = appId.getBytes(StandardCharsets.UTF_8);
    }

    public String decrypt(String encrypted) {
        try {
            byte[] ciphertext = Base64.getDecoder().decode(encrypted);
            if (ciphertext.length == 0 || ciphertext.length % 16 != 0) {
                throw new IllegalArgumentException("encrypted payload length is invalid");
            }
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(aesKey, "AES"),
                    new IvParameterSpec(aesKey, 0, 16));
            byte[] plain = removePadding(cipher.doFinal(ciphertext));
            if (plain.length < RANDOM_PREFIX_BYTES + LENGTH_BYTES) {
                throw new IllegalArgumentException("decrypted payload is incomplete");
            }
            ByteBuffer buffer = ByteBuffer.wrap(plain);
            buffer.position(RANDOM_PREFIX_BYTES);
            int messageLength = buffer.getInt();
            if (messageLength < 0 || messageLength > buffer.remaining()) {
                throw new IllegalArgumentException("decrypted message length is invalid");
            }
            byte[] message = new byte[messageLength];
            buffer.get(message);
            byte[] appId = new byte[buffer.remaining()];
            buffer.get(appId);
            if (!MessageDigest.isEqual(expectedAppId, appId)) {
                throw new IllegalArgumentException("decrypted AppID does not match");
            }
            return new String(message, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            throw new IllegalArgumentException("invalid encrypted WeChat payload", error);
        }
    }

    private static byte[] removePadding(byte[] value) {
        if (value.length == 0) {
            throw new IllegalArgumentException("missing PKCS7 padding");
        }
        int padding = Byte.toUnsignedInt(value[value.length - 1]);
        if (padding < 1 || padding > PKCS7_BLOCK_SIZE || padding > value.length) {
            throw new IllegalArgumentException("invalid PKCS7 padding");
        }
        for (int index = value.length - padding; index < value.length; index++) {
            if (Byte.toUnsignedInt(value[index]) != padding) {
                throw new IllegalArgumentException("invalid PKCS7 padding");
            }
        }
        return Arrays.copyOf(value, value.length - padding);
    }
}
