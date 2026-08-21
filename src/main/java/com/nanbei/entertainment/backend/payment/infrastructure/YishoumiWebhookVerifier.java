package com.nanbei.entertainment.backend.payment.infrastructure;

import com.nanbei.entertainment.backend.common.config.YishoumiPaymentProperties;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.payment.application.PaymentProvider;
import java.time.Instant;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

final class YishoumiWebhookVerifier {
    private final YishoumiPaymentProperties properties;
    private final YishoumiSigner signer;
    private final YishoumiJsonCodec jsonCodec;

    YishoumiWebhookVerifier(
            YishoumiPaymentProperties properties,
            YishoumiSigner signer,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.signer = signer;
        this.jsonCodec = new YishoumiJsonCodec(objectMapper);
    }

    PaymentProvider.VerifiedPaymentCallback verify(String rawBody) {
        if (!properties.configured()) {
            throw new ApiException(
                    ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE,
                    "支付渠道尚未配置");
        }
        Map<String, String> fields = jsonCodec.decode(rawBody);
        if (!signer.verify(
                fields, properties.appSecret(), fields.get("hash"))) {
            throw invalid("支付通知签名无效");
        }
        if (!properties.appId().equals(fields.get("appid"))) {
            throw invalid("支付通知 AppID 不匹配");
        }
        if (!"SUCCESS".equals(fields.get("state"))) {
            throw invalid("支付通知状态无效");
        }
        String merchantOrderNo = required(fields, "mch_orderid");
        String providerOrderNo = required(fields, "ysm_orderid");
        required(fields, "transaction_id");
        long amountMinor = positiveLong(fields, "total_fee");
        long paidAt = positiveLong(fields, "success_time");
        return new PaymentProvider.VerifiedPaymentCallback(
                providerOrderNo,
                merchantOrderNo,
                providerOrderNo,
                amountMinor,
                "CNY",
                Instant.ofEpochSecond(paidAt));
    }

    private static String required(
            Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value == null || value.isBlank()) {
            throw invalid("支付通知缺少必要字段");
        }
        return value;
    }

    private static long positiveLong(
            Map<String, String> fields, String name) {
        try {
            long value = Long.parseLong(required(fields, name));
            if (value <= 0L) {
                throw invalid("支付通知数值字段无效");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw invalid("支付通知数值字段无效");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.PAYMENT_CALLBACK_INVALID, message);
    }
}
