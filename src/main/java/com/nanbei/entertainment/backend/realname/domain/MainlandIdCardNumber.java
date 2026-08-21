package com.nanbei.entertainment.backend.realname.domain;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

public record MainlandIdCardNumber(String value, LocalDate birthDate) {
    private static final Pattern FORMAT =
            Pattern.compile("^[1-9]\\d{16}[\\dX]$");
    private static final int[] WEIGHTS = {
        7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2
    };
    private static final String CHECK_CODES = "10X98765432";
    private static final int ADULT_AGE = 18;

    public static MainlandIdCardNumber parse(String rawValue) {
        String normalized =
                rawValue == null
                        ? ""
                        : rawValue.trim().replace('x', 'X');
        if (!FORMAT.matcher(normalized).matches()) {
            throw invalid();
        }
        LocalDate birthDate = parseBirthDate(normalized);
        if (birthDate == null || !checkDigitMatches(normalized)) {
            throw invalid();
        }
        return new MainlandIdCardNumber(normalized, birthDate);
    }

    public boolean isAdult() {
        return isAdultOn(LocalDate.now());
    }

    public boolean isAdultOn(LocalDate date) {
        return !birthDate.plusYears(ADULT_AGE).isAfter(date);
    }

    private static LocalDate parseBirthDate(String value) {
        try {
            return LocalDate.parse(
                    value.substring(6, 14),
                    java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static boolean checkDigitMatches(String value) {
        int sum = 0;
        for (int index = 0; index < WEIGHTS.length; index++) {
            sum += (value.charAt(index) - '0') * WEIGHTS[index];
        }
        return CHECK_CODES.charAt(sum % 11) == value.charAt(17);
    }

    private static ApiException invalid() {
        return new ApiException(
                ErrorCode.REALNAME_INVALID_FORMAT, "身份证号格式不正确");
    }
}
