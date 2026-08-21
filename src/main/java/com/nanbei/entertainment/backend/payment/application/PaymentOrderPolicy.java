package com.nanbei.entertainment.backend.payment.application;

import com.nanbei.entertainment.backend.payment.domain.PaymentProductEntity;
import java.util.UUID;

public interface PaymentOrderPolicy {
    void validate(UUID userId, PaymentProductEntity product);
}
