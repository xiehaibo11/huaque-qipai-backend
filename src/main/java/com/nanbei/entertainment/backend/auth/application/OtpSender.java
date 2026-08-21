package com.nanbei.entertainment.backend.auth.application;

public interface OtpSender {
    void send(String phoneNumber, String code);
}
