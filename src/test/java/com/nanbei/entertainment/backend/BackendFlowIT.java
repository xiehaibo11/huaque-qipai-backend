package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.payment.infrastructure.PaymentOutboxRepository;
import com.nanbei.entertainment.backend.region.infrastructure.UserRegionSelectionRepository;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Import(BackendFlowTestcontainersConfiguration.class)
class BackendFlowIT extends BackendFlowTestSupport {
    @Test
    void completesOtpLoginRefreshAndMockPayment() throws Exception {
        HttpResponse<String> otpResponse =
                post(
                        "/api/v1/auth/otp/request",
                        "{\"phoneNumber\":\"13800138000\"}",
                        null,
                        null,
                        null);
        assertThat(otpResponse.statusCode()).isEqualTo(202);

        JsonNode tokens =
                json(
                        post(
                                        "/api/v1/auth/otp/verify",
                                        "{\"phoneNumber\":\"13800138000\",\"code\":\"246810\"}",
                                        null,
                                        null,
                                        null)
                                .body());
        String accessToken = tokens.path("accessToken").asText();
        String refreshToken = tokens.path("refreshToken").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();

        HttpResponse<String> me = get("/api/v1/users/me", accessToken);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(json(me.body()).path("displayName").asText())
                .endsWith("8000");

        assertThat(
                        post(
                                        "/api/v1/auth/providers/wechat/login",
                                        "{\"credential\":\"wechat-code\"}",
                                        null,
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(200);
        assertThat(
                        post(
                                        "/api/v1/auth/providers/one_tap/login",
                                        "{\"credential\":\"one-tap-token\"}",
                                        null,
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(200);

        JsonNode order =
                json(
                        post(
                                        "/api/v1/payments/orders",
                                        "{\"productCode\":\"LOCAL_COIN_PACK_1\",\"provider\":\"MOCK\"}",
                                        accessToken,
                                        "Idempotency-Key",
                                        "integration-order-1")
                                .body());
        String orderId = order.path("id").asText();
        String merchantOrderNo = order.path("merchantOrderNo").asText();
        String providerOrderNo = order.path("providerOrderNo").asText();
        assertThat(order.path("amountMinor").asLong()).isEqualTo(100);
        assertThat(order.path("status").asText()).isEqualTo("PENDING");

        JsonNode duplicateOrder =
                json(
                        post(
                                        "/api/v1/payments/orders",
                                        "{\"productCode\":\"LOCAL_COIN_PACK_1\",\"provider\":\"MOCK\"}",
                                        accessToken,
                                        "Idempotency-Key",
                                        "integration-order-1")
                                .body());
        assertThat(duplicateOrder.path("id").asText()).isEqualTo(orderId);

        long outboxCountBeforeCallback = outboxRepository.count();

        String callbackBody =
                objectMapper.writeValueAsString(
                        java.util.Map.of(
                                "eventId", "event-integration-1",
                                "merchantOrderNo", merchantOrderNo,
                                "providerOrderNo", providerOrderNo,
                                "amountMinor", 100,
                                "currency", "CNY",
                                "status", "PAID"));
        String signature =
                cryptoService.hmacSha256(
                        "local-mock-payment-secret", callbackBody);
        HttpResponse<String> callback =
                post(
                        "/api/v1/payments/webhooks/mock",
                        callbackBody,
                        null,
                        "X-Mock-Signature",
                        signature);
        assertThat(callback.statusCode()).isEqualTo(200);

        JsonNode paidOrder =
                json(get("/api/v1/payments/orders/" + orderId, accessToken).body());
        assertThat(paidOrder.path("status").asText()).isEqualTo("PAID");
        assertThat(outboxRepository.count()).isEqualTo(outboxCountBeforeCallback + 1);

        JsonNode duplicateCallback =
                json(
                        post(
                                        "/api/v1/payments/webhooks/mock",
                                        callbackBody,
                                        null,
                                        "X-Mock-Signature",
                                        signature)
                                .body());
        assertThat(duplicateCallback.path("duplicate").asBoolean()).isTrue();
        assertThat(outboxRepository.count()).isEqualTo(outboxCountBeforeCallback + 1);

        JsonNode refreshed =
                json(
                        post(
                                        "/api/v1/auth/refresh",
                                        "{\"refreshToken\":\"" + refreshToken + "\"}",
                                        null,
                                        null,
                                        null)
                                .body());
        String rotatedRefreshToken = refreshed.path("refreshToken").asText();
        assertThat(rotatedRefreshToken)
                .isNotEqualTo(refreshToken);
        HttpResponse<String> reused =
                post(
                        "/api/v1/auth/refresh",
                        "{\"refreshToken\":\"" + refreshToken + "\"}",
                        null,
                        null,
                        null);
        assertThat(reused.statusCode()).isEqualTo(401);
        assertThat(json(reused.body()).path("code").asText())
                .isEqualTo("AUTH_REFRESH_REUSED");

        HttpResponse<String> revokedFamily =
                post(
                        "/api/v1/auth/refresh",
                        "{\"refreshToken\":\"" + rotatedRefreshToken + "\"}",
                        null,
                        null,
                        null);
        assertThat(revokedFamily.statusCode()).isEqualTo(401);
        assertThat(json(revokedFamily.body()).path("code").asText())
                .isEqualTo("AUTH_REFRESH_REUSED");
    }
}
