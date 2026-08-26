package com.nanbei.entertainment.backend.wechatpush.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

public final class WeChatPushSignature {
    private final String token;

    public WeChatPushSignature(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("WeChat push token is required");
        }
        this.token = token;
    }

    public boolean matches(String signature, String timestamp, String nonce) {
        return constantEquals(signature, sign(timestamp, nonce));
    }

    public boolean matchesEncrypted(
            String signature, String timestamp, String nonce, String encrypted) {
        return constantEquals(signature, sign(timestamp, nonce, encrypted));
    }

    public String sign(String timestamp, String nonce, String... values) {
        require(timestamp, "timestamp");
        require(nonce, "nonce");
        String[] parts = new String[values.length + 3];
        parts[0] = token;
        parts[1] = timestamp;
        parts[2] = nonce;
        for (int index = 0; index < values.length; index++) {
            parts[index + 3] = require(values[index], "signature value");
        }
        Arrays.sort(parts);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            for (String part : parts) {
                digest.update(part.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-1 is unavailable", impossible);
        }
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static boolean constantEquals(String supplied, String expected) {
        return supplied != null
                && MessageDigest.isEqual(
                        supplied.getBytes(StandardCharsets.US_ASCII),
                        expected.getBytes(StandardCharsets.US_ASCII));
    }
}
