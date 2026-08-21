package com.nanbei.entertainment.backend.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentOrderEntityTest {
    @Test
    void merchantOrderNumberFitsYishoumiContract() {
        PaymentProductEntity product =
                new PaymentProductEntity(
                        "SXVIP_365_DAYS", "365天会员", 26_800L, "CNY");

        PaymentOrderEntity order =
                new PaymentOrderEntity(
                        java.util.UUID.randomUUID(),
                        product,
                        PaymentProviderType.MOCK,
                        "merchant-order-contract");

        assertThat(order.getMerchantOrderNo())
                .hasSizeBetween(6, 32)
                .matches("[A-Za-z0-9_-]+");
    }
}
