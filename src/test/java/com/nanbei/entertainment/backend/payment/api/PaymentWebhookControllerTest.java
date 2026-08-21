package com.nanbei.entertainment.backend.payment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.payment.application.PaymentService;
import com.nanbei.entertainment.backend.payment.domain.PaymentProviderType;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class PaymentWebhookControllerTest {
    @Test
    void returnsExactLowercasePlainTextSuccessForYishoumi() {
        PaymentService paymentService = mock(PaymentService.class);
        PaymentWebhookController controller =
                new PaymentWebhookController(paymentService);
        String rawBody =
                "{\"appid\":\"app-1\",\"state\":\"SUCCESS\",\"hash\":\"signed\"}";
        when(paymentService.handleWebhook(
                        PaymentProviderType.YISHOUMI, rawBody, null))
                .thenReturn(new PaymentService.WebhookResult("SUCCESS", false));

        ResponseEntity<String> response = controller.yishoumi(rawBody);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(response.getBody()).isEqualTo("success");
        verify(paymentService)
                .handleWebhook(PaymentProviderType.YISHOUMI, rawBody, null);
    }
}
