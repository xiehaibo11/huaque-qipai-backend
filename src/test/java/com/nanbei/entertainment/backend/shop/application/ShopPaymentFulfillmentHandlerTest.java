package com.nanbei.entertainment.backend.shop.application;

import static org.mockito.Mockito.verify;

import com.nanbei.entertainment.backend.payment.domain.PaymentOrderEntity;
import com.nanbei.entertainment.backend.payment.domain.PaymentProductEntity;
import com.nanbei.entertainment.backend.payment.domain.PaymentProviderType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShopPaymentFulfillmentHandlerTest {
    @Mock ShopPurchaseService purchaseService;

    @Test
    void delegatesPaidOrdersUsingTheImmutablePaymentProductId() {
        PaymentProductEntity paymentProduct =
                new PaymentProductEntity("DIAMOND_100", "100钻石", 100, "CNY");
        UUID userId = UUID.randomUUID();
        PaymentOrderEntity order =
                new PaymentOrderEntity(
                        userId, paymentProduct, PaymentProviderType.MOCK, "shop-paid-order");

        new ShopPaymentFulfillmentHandler(purchaseService).fulfill(order);

        verify(purchaseService)
                .fulfillPaidOrder(userId, order.getId(), paymentProduct.getId());
    }
}
