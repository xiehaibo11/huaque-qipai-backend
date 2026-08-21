package com.nanbei.entertainment.backend.auth.infrastructure;

final class SmsGatewayRequestException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String providerCode;
    private final String requestId;

    SmsGatewayRequestException(String providerCode, String requestId, Throwable cause) {
        super("Alibaba Cloud SMS request failed", cause);
        this.providerCode = providerCode;
        this.requestId = requestId;
    }

    String providerCode() {
        return providerCode;
    }

    String requestId() {
        return requestId;
    }
}
