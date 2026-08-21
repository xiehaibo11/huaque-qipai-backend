package com.nanbei.entertainment.backend.payment.application;

import com.nanbei.entertainment.backend.payment.domain.PaymentOrderEntity;

public interface PaymentFulfillmentHandler {
    void fulfill(PaymentOrderEntity order);
}
