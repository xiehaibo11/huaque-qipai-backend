package com.nanbei.entertainment.backend.payment.application;

import com.nanbei.entertainment.backend.payment.domain.PaymentOrderEntity;
import com.nanbei.entertainment.backend.payment.domain.PaymentProductEntity;
import com.nanbei.entertainment.backend.payment.domain.PaymentProviderType;
import java.time.Instant;
import java.util.Map;

public interface PaymentProvider {
    boolean supports(PaymentProviderType provider);

    PaymentCreation createPayment(
            PaymentOrderEntity order, PaymentProductEntity product);

    VerifiedPaymentCallback verifyWebhook(String rawBody, String signature);

    record PaymentCreation(String providerOrderNo, Map<String, String> parameters) {}

    record VerifiedPaymentCallback(
            String eventId,
            String merchantOrderNo,
            String providerOrderNo,
            long amountMinor,
            String currency,
            Instant paidAt) {}
}
