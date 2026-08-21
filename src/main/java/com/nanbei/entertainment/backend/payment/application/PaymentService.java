package com.nanbei.entertainment.backend.payment.application;

import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.payment.domain.PaymentOrderEntity;
import com.nanbei.entertainment.backend.payment.domain.PaymentOutboxEntity;
import com.nanbei.entertainment.backend.payment.domain.PaymentProductEntity;
import com.nanbei.entertainment.backend.payment.domain.PaymentProviderType;
import com.nanbei.entertainment.backend.payment.domain.PaymentWebhookEventEntity;
import com.nanbei.entertainment.backend.payment.infrastructure.PaymentOrderRepository;
import com.nanbei.entertainment.backend.payment.infrastructure.PaymentOutboxRepository;
import com.nanbei.entertainment.backend.payment.infrastructure.PaymentProductRepository;
import com.nanbei.entertainment.backend.payment.infrastructure.PaymentWebhookEventRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class PaymentService {
    private final PaymentProductRepository productRepository;
    private final PaymentOrderRepository orderRepository;
    private final PaymentWebhookEventRepository webhookRepository;
    private final PaymentOutboxRepository outboxRepository;
    private final List<PaymentProvider> paymentProviders;
    private final List<PaymentOrderPolicy> orderPolicies;
    private final List<PaymentFulfillmentHandler> fulfillmentHandlers;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;

    public PaymentService(
            PaymentProductRepository productRepository,
            PaymentOrderRepository orderRepository,
            PaymentWebhookEventRepository webhookRepository,
            PaymentOutboxRepository outboxRepository,
            List<PaymentProvider> paymentProviders,
            List<PaymentOrderPolicy> orderPolicies,
            List<PaymentFulfillmentHandler> fulfillmentHandlers,
            CryptoService cryptoService,
            ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.webhookRepository = webhookRepository;
        this.outboxRepository = outboxRepository;
        this.paymentProviders = paymentProviders;
        this.orderPolicies = orderPolicies;
        this.fulfillmentHandlers = fulfillmentHandlers;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PaymentProductEntity> listProducts() {
        return productRepository.findByEnabledTrueOrderByAmountMinorAsc();
    }

    @Transactional
    public PaymentOrderResponse createOrder(
            UUID userId,
            String productCode,
            PaymentProviderType providerType,
            String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED, "Idempotency-Key 不能为空");
        }
        // Keep the existing transaction serialized through provider creation so
        // a concurrent retry cannot observe a partially initialized CREATED order.
        orderRepository.acquireIdempotencyLock(
                userId + ":" + idempotencyKey);
        var existing =
                orderRepository.findByUserIdAndIdempotencyKey(
                        userId, idempotencyKey);
        if (existing.isPresent()) {
            return PaymentOrderResponse.from(existing.get(), Map.of());
        }
        PaymentProductEntity product =
                productRepository
                        .findByProductCodeAndEnabledTrue(productCode)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.PAYMENT_PRODUCT_NOT_FOUND,
                                                "商品不存在或已下架"));
        orderPolicies.forEach(policy -> policy.validate(userId, product));
        PaymentProvider provider = provider(providerType);
        PaymentOrderEntity order =
                orderRepository.save(
                        new PaymentOrderEntity(
                                userId, product, providerType, idempotencyKey));
        try {
            PaymentProvider.PaymentCreation creation =
                    provider.createPayment(order, product);
            order.markPending(creation.providerOrderNo());
            return PaymentOrderResponse.from(order, creation.parameters());
        } catch (ApiException exception) {
            order.markFailed(exception.code().name());
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public PaymentOrderResponse getOrder(UUID userId, UUID orderId) {
        PaymentOrderEntity order =
                orderRepository
                        .findById(orderId)
                        .filter(candidate -> candidate.getUserId().equals(userId))
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.PAYMENT_ORDER_NOT_FOUND,
                                                "支付订单不存在"));
        return PaymentOrderResponse.from(order, Map.of());
    }

    @Transactional
    public WebhookResult handleWebhook(
            PaymentProviderType providerType,
            String rawBody,
            String signature) {
        PaymentProvider provider = provider(providerType);
        PaymentProvider.VerifiedPaymentCallback callback =
                provider.verifyWebhook(rawBody, signature);
        if (callback.eventId() == null || callback.eventId().isBlank()) {
            throw new ApiException(
                    ErrorCode.PAYMENT_CALLBACK_INVALID, "回调事件 ID 为空");
        }
        String payloadHash = cryptoService.sha256(rawBody);
        webhookRepository.acquireEventLock(
                providerType + ":" + callback.eventId());
        Optional<WebhookResult> duplicate =
                duplicateWebhookResult(
                        providerType, callback.eventId(), payloadHash);
        if (duplicate.isPresent()) {
            return duplicate.get();
        }
        PaymentOrderEntity order =
                orderRepository
                        .findLockedByMerchantOrderNo(callback.merchantOrderNo())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.PAYMENT_ORDER_NOT_FOUND,
                                                "支付订单不存在"));
        duplicate =
                duplicateWebhookResult(
                        providerType, callback.eventId(), payloadHash);
        if (duplicate.isPresent()) {
            return duplicate.get();
        }
        if (order.getProvider() != providerType) {
            throw new ApiException(
                    ErrorCode.PAYMENT_CALLBACK_INVALID, "支付渠道不匹配");
        }
        if (order.getAmountMinor() != callback.amountMinor()
                || !order.getCurrency().equals(callback.currency())) {
            throw new ApiException(
                    ErrorCode.PAYMENT_AMOUNT_MISMATCH, "回调金额或币种不匹配");
        }
        boolean newlyPaid =
                order.markPaid(callback.providerOrderNo(), callback.paidAt());
        webhookRepository.save(
                new PaymentWebhookEventEntity(
                        providerType,
                        callback.eventId(),
                        payloadHash,
                        order.getId()));
        if (newlyPaid
                && !outboxRepository.existsByEventTypeAndAggregateId(
                        "PAYMENT_SUCCEEDED", order.getId())) {
            outboxRepository.save(
                    new PaymentOutboxEntity(
                            order.getId(), outboxPayload(order)));
        }
        if (newlyPaid) {
            fulfillmentHandlers.forEach(handler -> handler.fulfill(order));
        }
        return new WebhookResult("SUCCESS", false);
    }

    private Optional<WebhookResult> duplicateWebhookResult(
            PaymentProviderType providerType,
            String eventId,
            String payloadHash) {
        return webhookRepository
                .findByProviderAndProviderEventId(providerType, eventId)
                .map(
                        existing -> {
                            if (!cryptoService.constantTimeEquals(
                                    existing.getPayloadHash(), payloadHash)) {
                                throw new ApiException(
                                        ErrorCode.PAYMENT_CALLBACK_INVALID,
                                        "回调事件 ID 已被不同报文使用");
                            }
                            return new WebhookResult("SUCCESS", true);
                        });
    }

    private PaymentProvider provider(PaymentProviderType type) {
        return paymentProviders.stream()
                .filter(candidate -> candidate.supports(type))
                .findFirst()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE,
                                        type + " 支付适配器尚未配置"));
    }

    private String outboxPayload(PaymentOrderEntity order) {
        try {
            return objectMapper.writeValueAsString(
                    Map.of(
                            "orderId", order.getId().toString(),
                            "merchantOrderNo", order.getMerchantOrderNo(),
                            "amountMinor", order.getAmountMinor(),
                            "currency", order.getCurrency()));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize outbox event", exception);
        }
    }

    public record WebhookResult(String status, boolean duplicate) {}
}
