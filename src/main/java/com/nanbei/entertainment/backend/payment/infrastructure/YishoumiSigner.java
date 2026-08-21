package com.nanbei.entertainment.backend.payment.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

final class YishoumiSigner {
    String sign(Map<String, ?> fields, String appSecret) {
        String canonical =
                new TreeMap<>(fields).entrySet().stream()
                        .filter(entry -> participatesInSignature(entry.getKey(), entry.getValue()))
                        .map(
                                entry ->
                                        entry.getKey()
                                                + "="
                                                + String.valueOf(entry.getValue()))
                        .collect(Collectors.joining("&"));
        return sha256(canonical + appSecret);
    }

    boolean verify(
            Map<String, ?> fields,
            String appSecret,
            String receivedSignature) {
        if (receivedSignature == null || receivedSignature.isBlank()) {
            return false;
        }
        byte[] expected = sign(fields, appSecret).getBytes(StandardCharsets.US_ASCII);
        byte[] received = receivedSignature.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, received);
    }

    private static boolean participatesInSignature(String key, Object value) {
        return key != null
                && !key.equals("sign")
                && !key.equals("hash")
                && value != null
                && (!(value instanceof String text) || !text.isEmpty());
    }

    private static String sha256(String value) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
