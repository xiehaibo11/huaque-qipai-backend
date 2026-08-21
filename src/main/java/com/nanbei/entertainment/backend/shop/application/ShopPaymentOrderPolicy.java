package com.nanbei.entertainment.backend.shop.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.payment.application.PaymentOrderPolicy;
import com.nanbei.entertainment.backend.payment.domain.PaymentProductEntity;
import com.nanbei.entertainment.backend.payment.infrastructure.PaymentOrderRepository;
import com.nanbei.entertainment.backend.shop.domain.ShopProductEntity;
import com.nanbei.entertainment.backend.shop.infrastructure.ShopProductRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ShopPaymentOrderPolicy implements PaymentOrderPolicy {
    private static final ZoneId CHINA_TIME = ZoneId.of("Asia/Shanghai");

    private final ShopProductRepository productRepository;
    private final PaymentOrderRepository orderRepository;

    public ShopPaymentOrderPolicy(
            ShopProductRepository productRepository,
            PaymentOrderRepository orderRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    public void validate(UUID userId, PaymentProductEntity paymentProduct) {
        ShopProductEntity product =
                productRepository
                        .findByPaymentProductIdAndEnabledTrue(paymentProduct.getId())
                        .orElse(null);
        if (product == null
                || (empty(product.getDailyLimit()) && empty(product.getLifetimeLimit()))) {
            return;
        }
        String date = LocalDate.now(CHINA_TIME).toString();
        orderRepository.acquireIdempotencyLock(
                "shop-limit:" + userId + ":" + paymentProduct.getId() + ":" + date);
        Integer lifetimeLimit = product.getLifetimeLimit();
        if (!empty(lifetimeLimit)
                && orderRepository.countActiveOrders(userId, paymentProduct.getId())
                        >= lifetimeLimit) {
            throw limitReached();
        }
        Integer dailyLimit = product.getDailyLimit();
        if (!empty(dailyLimit)
                && orderRepository.countActiveOrdersSince(
                                userId,
                                paymentProduct.getId(),
                                LocalDate.now(CHINA_TIME)
                                        .atStartOfDay(CHINA_TIME)
                                        .toInstant())
                        >= dailyLimit) {
            throw limitReached();
        }
    }

    private static boolean empty(Integer limit) {
        return limit == null || limit <= 0;
    }

    private static ApiException limitReached() {
        return new ApiException(ErrorCode.SHOP_DAILY_LIMIT_REACHED, "该商品购买次数已用完");
    }
}
