package com.nanbei.entertainment.backend.realname.domain;

public final class RealNameMasker {
    private RealNameMasker() {}

    public static String maskName(String realName) {
        String trimmed = realName == null ? "" : realName.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.charAt(0) + "*".repeat(trimmed.length() - 1);
    }

    public static String maskIdCard(String idCardNumber) {
        String trimmed = idCardNumber == null ? "" : idCardNumber.trim();
        if (trimmed.length() < 8) {
            return trimmed;
        }
        return trimmed.substring(0, 4)
                + "*".repeat(10)
                + trimmed.substring(trimmed.length() - 4);
    }
}
