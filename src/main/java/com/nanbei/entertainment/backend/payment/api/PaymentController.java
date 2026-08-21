package com.nanbei.entertainment.backend.payment.api;

import com.nanbei.entertainment.backend.payment.application.PaymentOrderResponse;
import com.nanbei.entertainment.backend.payment.application.PaymentService;
import com.nanbei.entertainment.backend.payment.domain.PaymentProductEntity;
import com.nanbei.entertainment.backend.payment.domain.PaymentProviderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/products")
    List<ProductResponse> products() {
        return paymentService.listProducts().stream()
                .map(ProductResponse::from)
                .toList();
    }

    @PostMapping("/orders")
    PaymentOrderResponse createOrder(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        return paymentService.createOrder(
                UUID.fromString(jwt.getSubject()),
                request.productCode(),
                PaymentProviderType.valueOf(
                        request.provider().toUpperCase(Locale.ROOT)),
                idempotencyKey);
    }

    @GetMapping("/orders/{orderId}")
    PaymentOrderResponse getOrder(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return paymentService.getOrder(
                UUID.fromString(jwt.getSubject()), orderId);
    }

    public record CreateOrderRequest(
            @NotBlank String productCode, @NotBlank String provider) {}

    public record ProductResponse(
            UUID id,
            String productCode,
            String name,
            long amountMinor,
            String currency) {
        static ProductResponse from(PaymentProductEntity product) {
            return new ProductResponse(
                    product.getId(),
                    product.getProductCode(),
                    product.getName(),
                    product.getAmountMinor(),
                    product.getCurrency());
        }
    }
}
