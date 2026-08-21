package com.nanbei.entertainment.backend.auth.infrastructure;

import com.nanbei.entertainment.backend.auth.application.OtpSender;
import com.nanbei.entertainment.backend.common.config.SmsProperties;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Profile("!local")
@ConditionalOnProperty(prefix = "nanbei.sms", name = "enabled", havingValue = "true")
public class AliyunSmsOtpSender implements OtpSender {
    private static final Logger LOGGER = LoggerFactory.getLogger(AliyunSmsOtpSender.class);
    private static final Pattern OTP_PATTERN = Pattern.compile("\\d{6}");
    private static final String PUBLIC_ERROR_MESSAGE = "短信验证码发送失败，请稍后重试";

    private final SmsProperties properties;
    private final SmsGateway gateway;

    public AliyunSmsOtpSender(SmsProperties properties, SmsGateway gateway) {
        this.properties = properties;
        this.gateway = gateway;
    }

    @Override
    public void send(String phoneNumber, String code) {
        validateConfiguration();
        if (code == null || !OTP_PATTERN.matcher(code).matches()) {
            throw providerUnavailable();
        }

        SmsGateway.SendResult result;
        try {
            result =
                    gateway.send(
                            new SmsGateway.SendCommand(
                                    phoneNumber,
                                    properties.signName(),
                                    properties.templateCode(),
                                    "{\"code\":\"" + code + "\"}"));
        } catch (SmsGatewayRequestException exception) {
            LOGGER.error(
                    "Alibaba Cloud SMS request failed; providerCode={}, requestId={}",
                    exception.providerCode(),
                    exception.requestId());
            throw providerUnavailable();
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Alibaba Cloud SMS request failed before a provider response; exceptionType={}",
                    exception.getClass().getName());
            throw providerUnavailable();
        }

        if (result == null || !"OK".equals(result.code())) {
            LOGGER.error(
                    "Alibaba Cloud SMS rejected the request; providerCode={}, requestId={}",
                    result == null ? null : result.code(),
                    result == null ? null : result.requestId());
            throw providerUnavailable();
        }
    }

    private void validateConfiguration() {
        if (properties.signName() == null
                || properties.signName().isBlank()
                || properties.templateCode() == null
                || properties.templateCode().isBlank()) {
            throw providerUnavailable();
        }
    }

    private ApiException providerUnavailable() {
        return new ApiException(ErrorCode.AUTH_PROVIDER_UNAVAILABLE, PUBLIC_ERROR_MESSAGE);
    }
}
