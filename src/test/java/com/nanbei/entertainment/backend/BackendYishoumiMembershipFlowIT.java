package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanbei.entertainment.backend.payment.infrastructure.YishoumiTransport;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "nanbei.payment.yishoumi.enabled=true",
            "nanbei.payment.yishoumi.app-id=test-yishoumi-app",
            "nanbei.payment.yishoumi.app-secret=test-yishoumi-secret",
            "nanbei.payment.yishoumi.payment-url=https://pay.example/create",
            "nanbei.payment.yishoumi.notify-url=https://api.example/api/v1/payments/webhooks/yishoumi",
            "nanbei.payment.yishoumi.callback-url=https://www.example/payment/result",
            "nanbei.payment.yishoumi.nopay-url=https://www.example/payment/result"
        })
@ActiveProfiles("local")
@Import({
    BackendFlowTestcontainersConfiguration.class,
    BackendYishoumiMembershipFlowIT.FakeTransportConfiguration.class
})
class BackendYishoumiMembershipFlowIT extends BackendFlowTestSupport {
    private static final String APP_ID = "test-yishoumi-app";
    private static final String APP_SECRET = "test-yishoumi-secret";

    @Autowired FakeYishoumiTransport fakeTransport;

    @Test
    void createsAlipayH5OrderAndFulfillsMembershipOnceAfterSignedWebhook()
            throws Exception {
        String phoneNumber = "13800138201";
        String accessToken = login(phoneNumber);
        UUID userId = userIdByPhone(phoneNumber);

        HttpResponse<String> created =
                post(
                        "/api/v1/payments/orders",
                        """
                        {"productCode":"SXVIP_7_DAYS","provider":"YISHOUMI"}
                        """,
                        accessToken,
                        "Idempotency-Key",
                        "yishoumi-flow-" + UUID.randomUUID());

        assertThat(created.statusCode()).isEqualTo(200);
        JsonNode order = json(created.body());
        assertThat(order.path("provider").asText()).isEqualTo("YISHOUMI");
        assertThat(order.path("status").asText()).isEqualTo("PENDING");
        assertThat(order.path("amountMinor").asLong()).isEqualTo(2_500L);
        assertThat(order.path("paymentParameters").path("payType").asText())
                .isEqualTo("11");
        assertThat(order.path("paymentParameters").path("paymentUrl").asText())
                .startsWith("https://pay.example/alipay/");
        assertThat(fakeTransport.lastRequest().get("payType")).isEqualTo(11);
        assertThat(fakeTransport.lastRequest().get("total")).isEqualTo(2_500L);
        assertThat(fakeTransport.lastRequest().get("mch_orderid"))
                .isEqualTo(order.path("merchantOrderNo").asText());

        String callbackBody = signedCallback(order);
        HttpResponse<String> webhook = postJson(callbackBody);

        assertThat(webhook.statusCode()).isEqualTo(200);
        assertThat(webhook.headers().firstValue("Content-Type").orElse(""))
                .startsWith("text/plain");
        assertThat(webhook.body()).isEqualTo("success");
        assertThat(postJson(callbackBody).body()).isEqualTo("success");

        JsonNode membership =
                json(get("/api/v1/membership/status", accessToken).body());
        assertThat(membership.path("membershipActive").asBoolean()).isTrue();
        assertThat(membership.path("remainingDays").asLong()).isBetween(6L, 7L);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from user_memberships where user_id = ?",
                                Long.class,
                                userId))
                .isEqualTo(1L);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from membership_reward_grants where user_id = ? and source_type = 'MEMBERSHIP_PURCHASE'",
                                Long.class,
                                userId))
                .isEqualTo(4L);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from payment_outbox where aggregate_id = ?",
                                Long.class,
                                UUID.fromString(order.path("id").asText())))
                .isEqualTo(1L);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from payment_webhook_events where provider = 'YISHOUMI' and provider_event_id = ?",
                                Long.class,
                                "YSM" + order.path("merchantOrderNo").asText()))
                .isEqualTo(1L);
    }

    private String login(String phoneNumber) throws Exception {
        assertThat(
                        post(
                                        "/api/v1/auth/otp/request",
                                        "{\"phoneNumber\":\"" + phoneNumber + "\"}",
                                        null,
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(202);
        JsonNode tokens =
                json(
                        post(
                                        "/api/v1/auth/otp/verify",
                                        "{\"phoneNumber\":\""
                                                + phoneNumber
                                                + "\",\"code\":\"246810\"}",
                                        null,
                                        null,
                                        null)
                                .body());
        return tokens.path("accessToken").asText();
    }

    private UUID userIdByPhone(String phoneNumber) {
        return jdbcTemplate.queryForObject(
                "select user_id from user_identities where provider = 'PHONE' and provider_subject = ?",
                UUID.class,
                phoneNumber);
    }

    private String signedCallback(JsonNode order) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("appid", APP_ID);
        fields.put("state", "SUCCESS");
        fields.put("mch_orderid", order.path("merchantOrderNo").asText());
        fields.put("ysm_orderid", "YSM" + order.path("merchantOrderNo").asText());
        fields.put("transaction_id", "ALI" + order.path("merchantOrderNo").asText());
        fields.put("total_fee", Long.toString(order.path("amountMinor").asLong()));
        fields.put("success_time", Long.toString(java.time.Instant.now().getEpochSecond()));
        fields.put("hash", sign(fields));
        try {
            return new ObjectMapper().writeValueAsString(fields);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private HttpResponse<String> postJson(String body) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(uri("/api/v1/payments/webhooks/yishoumi"))
                        .header(
                                "Content-Type",
                                "application/json; charset=UTF-8")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
        return httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());
    }

    private static String sign(Map<String, String> fields) {
        String canonical =
                new TreeMap<>(fields).entrySet().stream()
                        .filter(
                                entry ->
                                        !entry.getKey().equals("sign")
                                                && !entry.getKey().equals("hash")
                                                && !entry.getValue().isEmpty())
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining("&"));
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(
                                    (canonical + APP_SECRET)
                                            .getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeTransportConfiguration {
        @Bean
        @Primary
        FakeYishoumiTransport fakeYishoumiTransport(ObjectMapper objectMapper) {
            return new FakeYishoumiTransport(objectMapper);
        }
    }

    static final class FakeYishoumiTransport implements YishoumiTransport {
        private final ObjectMapper objectMapper;
        private final AtomicReference<Map<String, Object>> lastRequest =
                new AtomicReference<>();

        FakeYishoumiTransport(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public String postJson(java.net.URI endpoint, Map<String, ?> fields) {
            Map<String, Object> request = new LinkedHashMap<>();
            request.putAll(fields);
            lastRequest.set(Map.copyOf(request));
            Map<String, String> response = new LinkedHashMap<>();
            response.put("code", "0");
            response.put("ordeid", fields.get("mch_orderid").toString());
            response.put(
                    "url",
                    "https://pay.example/alipay/" + fields.get("mch_orderid"));
            response.put("sign", sign(response));
            try {
                return objectMapper.writeValueAsString(response);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        Map<String, Object> lastRequest() {
            return lastRequest.get();
        }
    }
}
