package com.nanbei.entertainment.backend.shop.application;

import com.nanbei.entertainment.backend.payment.application.PaymentFulfillmentHandler;
import com.nanbei.entertainment.backend.payment.domain.PaymentOrderEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ShopPaymentFulfillmentHandler implements PaymentFulfillmentHandler {
    private final ShopPurchaseService purchaseService;

    public ShopPaymentFulfillmentHandler(ShopPurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @Override
    @Transactional
    public void fulfill(PaymentOrderEntity order) {
        purchaseService.fulfillPaidOrder(
                order.getUserId(), order.getId(), order.getProductId());
    }
}
