package com.nanbei.entertainment.backend.payment.api;

import com.nanbei.entertainment.backend.payment.application.PaymentService;
import com.nanbei.entertainment.backend.payment.domain.PaymentProviderType;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/webhooks")
public class PaymentWebhookController {
    private final PaymentService paymentService;

    public PaymentWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping(
            value = "/yishoumi",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<String> yishoumi(@RequestBody String rawBody) {
        paymentService.handleWebhook(
                PaymentProviderType.YISHOUMI, rawBody, null);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body("success");
    }

    @PostMapping("/{provider}")
    PaymentService.WebhookResult webhook(
            @PathVariable String provider,
            @RequestHeader(value = "X-Mock-Signature", required = false)
                    String signature,
            @RequestBody String rawBody) {
        return paymentService.handleWebhook(
                PaymentProviderType.valueOf(
                        provider.toUpperCase(Locale.ROOT)),
                rawBody,
                signature);
    }
}
