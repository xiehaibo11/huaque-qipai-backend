package com.nanbei.entertainment.backend.payment.infrastructure;

import com.nanbei.entertainment.backend.common.config.PaymentProperties;
import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.payment.application.PaymentProvider;
import com.nanbei.entertainment.backend.payment.domain.PaymentOrderEntity;
import com.nanbei.entertainment.backend.payment.domain.PaymentProductEntity;
import com.nanbei.entertainment.backend.payment.domain.PaymentProviderType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("local")
public class MockPaymentProvider implements PaymentProvider {
    private final PaymentProperties properties;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;

    public MockPaymentProvider(
            PaymentProperties properties,
            CryptoService cryptoService,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(PaymentProviderType provider) {
        return provider == PaymentProviderType.MOCK;
    }

    @Override
    public PaymentCreation createPayment(
            PaymentOrderEntity order, PaymentProductEntity product) {
        String providerOrderNo = "MOCK-" + UUID.randomUUID();
        return new PaymentCreation(
                providerOrderNo,
                Map.of(
                        "providerOrderNo", providerOrderNo,
                        "paymentToken", cryptoService.randomToken()));
    }

    @Override
    public VerifiedPaymentCallback verifyWebhook(
            String rawBody, String signature) {
        String expected =
                cryptoService.hmacSha256(
                        properties.mockWebhookSecret(), rawBody);
        if (signature == null
                || !cryptoService.constantTimeEquals(expected, signature)) {
            throw new ApiException(
                    ErrorCode.PAYMENT_CALLBACK_INVALID, "Mock 回调签名无效");
        }
        try {
            JsonNode node = objectMapper.readTree(rawBody);
            if (!"PAID".equals(node.path("status").asText())) {
                throw new ApiException(
                        ErrorCode.PAYMENT_CALLBACK_INVALID,
                        "Mock 回调状态必须为 PAID");
            }
            return new VerifiedPaymentCallback(
                    node.path("eventId").asText(),
                    node.path("merchantOrderNo").asText(),
                    node.path("providerOrderNo").asText(),
                    node.path("amountMinor").asLong(),
                    node.path("currency").asText(),
                    Instant.now());
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(
                    ErrorCode.PAYMENT_CALLBACK_INVALID, "Mock 回调 JSON 无效");
        }
    }
}
