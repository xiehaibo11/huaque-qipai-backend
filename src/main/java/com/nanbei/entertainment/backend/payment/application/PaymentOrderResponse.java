package com.nanbei.entertainment.backend.payment.application;

import com.nanbei.entertainment.backend.payment.domain.PaymentOrderEntity;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PaymentOrderResponse(
        UUID id,
        String merchantOrderNo,
        String provider,
        long amountMinor,
        String currency,
        String status,
        String providerOrderNo,
        Instant paidAt,
        Instant createdAt,
        Map<String, String> paymentParameters) {
    public static PaymentOrderResponse from(
            PaymentOrderEntity order, Map<String, String> parameters) {
        return new PaymentOrderResponse(
                order.getId(),
                order.getMerchantOrderNo(),
                order.getProvider().name(),
                order.getAmountMinor(),
                order.getCurrency(),
                order.getStatus().name(),
                order.getProviderOrderNo(),
                order.getPaidAt(),
                order.getCreatedAt(),
                parameters);
    }
}
