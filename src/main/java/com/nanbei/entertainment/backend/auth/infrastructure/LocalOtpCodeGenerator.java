package com.nanbei.entertainment.backend.auth.infrastructure;

import com.nanbei.entertainment.backend.auth.application.OtpCodeGenerator;
import com.nanbei.entertainment.backend.common.config.AuthProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalOtpCodeGenerator implements OtpCodeGenerator {
    private final AuthProperties authProperties;

    public LocalOtpCodeGenerator(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @Override
    public String generate() {
        String code = authProperties.localOtp();
        if (code == null || !code.matches("\\d{6}")) {
            throw new IllegalStateException("LOCAL_OTP 必须配置为六位数字");
        }
        return code;
    }
}
