package com.nanbei.entertainment.backend.auth.infrastructure;

import com.nanbei.entertainment.backend.auth.application.OtpSender;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local")
@ConditionalOnProperty(
        prefix = "nanbei.sms",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true)
public class UnavailableOtpSender implements OtpSender {
    @Override
    public void send(String phoneNumber, String code) {
        throw new ApiException(
                ErrorCode.AUTH_PROVIDER_UNAVAILABLE,
                "短信验证码服务尚未配置");
    }
}
