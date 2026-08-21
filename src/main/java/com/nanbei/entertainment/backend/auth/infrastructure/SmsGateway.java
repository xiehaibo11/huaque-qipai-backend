package com.nanbei.entertainment.backend.auth.infrastructure;

public interface SmsGateway {
    SendResult send(SendCommand command);

    record SendCommand(
            String phoneNumber,
            String signName,
            String templateCode,
            String templateParam) {
    }

    record SendResult(String code, String message, String requestId) {
    }
}
