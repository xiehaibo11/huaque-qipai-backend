package com.nanbei.entertainment.backend.auth.infrastructure;

public interface DypnsClient {
    Result getMobile(String accessToken, String outId);

    record Result(
            String code, String mobile, String requestId) {}

    final class RequestException extends RuntimeException {
        private final String providerCode;
        private final String requestId;

        RequestException(
                String providerCode,
                String requestId,
                Throwable cause) {
            super(
                    "Dypnsapi request failed; providerCode="
                            + safe(providerCode)
                            + ", requestId="
                            + safe(requestId),
                    cause);
            this.providerCode = providerCode;
            this.requestId = requestId;
        }

        String providerCode() {
            return providerCode;
        }

        String requestId() {
            return requestId;
        }

        private static String safe(String value) {
            return value == null || value.isBlank()
                    ? "unknown"
                    : value;
        }
    }
}
