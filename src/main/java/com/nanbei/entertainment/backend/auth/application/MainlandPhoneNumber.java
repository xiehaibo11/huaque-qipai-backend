package com.nanbei.entertainment.backend.auth.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import java.util.regex.Pattern;

public record MainlandPhoneNumber(String value) {
    private static final Pattern MAINLAND_MOBILE = Pattern.compile("^1[3-9]\\d{9}$");

    public static MainlandPhoneNumber parse(String rawValue) {
        String normalized =
                rawValue == null
                        ? ""
                        : rawValue.replaceAll("[\\s-]", "");
        if (normalized.startsWith("+86")) {
            normalized = normalized.substring(3);
        } else if (normalized.startsWith("0086")) {
            normalized = normalized.substring(4);
        } else if (normalized.startsWith("86") && normalized.length() == 13) {
            normalized = normalized.substring(2);
        }
        if (!MAINLAND_MOBILE.matcher(normalized).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "手机号格式不正确");
        }
        return new MainlandPhoneNumber(normalized);
    }
}
