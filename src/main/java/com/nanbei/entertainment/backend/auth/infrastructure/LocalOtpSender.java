package com.nanbei.entertainment.backend.auth.infrastructure;

import com.nanbei.entertainment.backend.auth.application.OtpSender;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalOtpSender implements OtpSender {
    @Override
    public void send(String phoneNumber, String code) {
        // Local profile intentionally uses the configured fixed OTP.
    }
}
