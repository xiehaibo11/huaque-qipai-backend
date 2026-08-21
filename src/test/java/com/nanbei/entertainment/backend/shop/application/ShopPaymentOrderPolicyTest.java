package com.nanbei.entertainment.backend.shop.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.payment.domain.PaymentProductEntity;
import com.nanbei.entertainment.backend.payment.infrastructure.PaymentOrderRepository;
import com.nanbei.entertainment.backend.shop.domain.ShopProductEntity;
import com.nanbei.entertainment.backend.shop.infrastructure.ShopProductRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShopPaymentOrderPolicyTest {
    @Mock ShopProductRepository productRepository;
    @Mock PaymentOrderRepository orderRepository;

    @Test
    void rejectsAnotherActiveOrderWhenOriginalDailyLimitIsReached() {
        PaymentProductEntity paymentProduct =
                new PaymentProductEntity("HOT_FIRST_RECHARGE", "首充礼包", 600, "CNY");
        ShopProductEntity shopProduct =
                ShopProductEntity.paid(
                        "HOT_FIRST_RECHARGE",
                        "hot_recommendation",
                        "首充礼包",
                        "diamond",
                        600,
                        "DIAMOND",
                        100,
                        201,
                        null,
                        1,
                        paymentProduct.getId());
        UUID userId = UUID.randomUUID();
        when(productRepository.findByPaymentProductIdAndEnabledTrue(paymentProduct.getId()))
                .thenReturn(Optional.of(shopProduct));
        when(orderRepository.countActiveOrders(userId, paymentProduct.getId()))
                .thenReturn(1L);

        ShopPaymentOrderPolicy policy =
                new ShopPaymentOrderPolicy(productRepository, orderRepository);

        assertThatThrownBy(() -> policy.validate(userId, paymentProduct))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo(ErrorCode.SHOP_DAILY_LIMIT_REACHED);
        verify(orderRepository).acquireIdempotencyLock(any(String.class));
    }

    @Test
    void ignoresPaymentProductsThatAreNotDailyLimitedShopProducts() {
        PaymentProductEntity paymentProduct =
                new PaymentProductEntity("DIAMOND_100", "100钻石", 100, "CNY");
        when(productRepository.findByPaymentProductIdAndEnabledTrue(paymentProduct.getId()))
                .thenReturn(Optional.empty());

        new ShopPaymentOrderPolicy(productRepository, orderRepository)
                .validate(UUID.randomUUID(), paymentProduct);

        verify(orderRepository, never()).countActiveOrdersSince(any(), any(), any());
        verify(orderRepository, never()).countActiveOrders(any(), any());
    }
}
