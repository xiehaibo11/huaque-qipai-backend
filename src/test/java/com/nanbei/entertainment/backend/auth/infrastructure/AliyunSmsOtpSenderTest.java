package com.nanbei.entertainment.backend.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import com.nanbei.entertainment.backend.common.config.SmsProperties;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AliyunSmsOtpSenderTest {
    @Test
    void sendsConfiguredSignatureTemplateAndCode() {
        AtomicReference<SmsGateway.SendCommand> captured = new AtomicReference<>();
        SmsGateway gateway =
                command -> {
                    captured.set(command);
                    return new SmsGateway.SendResult("OK", "OK", "request-1");
                };
        AliyunSmsOtpSender sender =
                new AliyunSmsOtpSender(
                        new SmsProperties(
                                true,
                                "cn-hangzhou",
                                "广东万一塑胶材料有限公司",
                                "SMS_123456789"),
                        gateway);

        sender.send("13800138000", "123456");

        assertThat(captured.get().phoneNumber()).isEqualTo("13800138000");
        assertThat(captured.get().signName()).isEqualTo("广东万一塑胶材料有限公司");
        assertThat(captured.get().templateCode()).isEqualTo("SMS_123456789");
        assertThat(captured.get().templateParam()).isEqualTo("{\"code\":\"123456\"}");
    }

    @Test
    void rejectsMissingTemplateCode() {
        AliyunSmsOtpSender sender =
                new AliyunSmsOtpSender(
                        new SmsProperties(
                                true,
                                "cn-hangzhou",
                                "广东万一塑胶材料有限公司",
                                ""),
                        command -> new SmsGateway.SendResult("OK", "OK", "request-1"));

        assertProviderUnavailable(() -> sender.send("13800138000", "123456"));
    }

    @Test
    void mapsAliyunFailureToPublicProviderError() {
        Logger logger = (Logger) LoggerFactory.getLogger(AliyunSmsOtpSender.class);
        ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        AliyunSmsOtpSender sender =
                new AliyunSmsOtpSender(
                        new SmsProperties(
                                true,
                                "cn-hangzhou",
                                "广东万一塑胶材料有限公司",
                                "SMS_123456789"),
                        command ->
                                new SmsGateway.SendResult(
                                        "isv.SMS_SIGNATURE_ILLEGAL",
                                        "signature rejected",
                                        "request-2"));

        try {
            assertProviderUnavailable(() -> sender.send("13800138000", "123456"));

            assertThat(appender.list)
                    .extracting(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .anySatisfy(
                            message -> {
                                assertThat(message)
                                        .contains("isv.SMS_SIGNATURE_ILLEGAL", "request-2");
                                assertThat(message)
                                        .doesNotContain(
                                                "13800138000",
                                                "123456",
                                                "signature rejected");
                            });
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void logsSafeProviderMetadataWhenGatewayThrows() {
        Logger logger = (Logger) LoggerFactory.getLogger(AliyunSmsOtpSender.class);
        ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        AliyunSmsOtpSender sender =
                new AliyunSmsOtpSender(
                        new SmsProperties(
                                true,
                                "cn-hangzhou",
                                "广东万一塑胶材料有限公司",
                                "SMS_123456789"),
                        command ->
                                {
                                    throw new SmsGatewayRequestException(
                                            "InvalidAccessKeyId.NotFound",
                                            "request-3",
                                            new RuntimeException("provider detail"));
                                });

        try {
            assertProviderUnavailable(() -> sender.send("13800138000", "123456"));

            assertThat(appender.list)
                    .extracting(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .anySatisfy(
                            message -> {
                                assertThat(message)
                                        .contains("InvalidAccessKeyId.NotFound", "request-3");
                                assertThat(message)
                                        .doesNotContain(
                                                "13800138000",
                                                "123456",
                                                "provider detail");
                            });
        } finally {
            logger.detachAppender(appender);
        }
    }

    private void assertProviderUnavailable(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.code())
                                        .isEqualTo(ErrorCode.AUTH_PROVIDER_UNAVAILABLE));
    }
}
