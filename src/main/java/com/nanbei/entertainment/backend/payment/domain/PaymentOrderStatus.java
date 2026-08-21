package com.nanbei.entertainment.backend.payment.domain;

public enum PaymentOrderStatus {
    CREATED,
    PENDING,
    PAID,
    FAILED,
    CANCELLED,
    REFUNDED
}
