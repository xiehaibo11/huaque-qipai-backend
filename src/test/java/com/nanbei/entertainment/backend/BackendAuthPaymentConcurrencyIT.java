package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Import(BackendFlowTestcontainersConfiguration.class)
class BackendAuthPaymentConcurrencyIT extends BackendFlowTestSupport {
    private static final Duration CONTENTION_TIMEOUT = Duration.ofSeconds(2);
    @Autowired DataSource dataSource;
    @Test
    void permitsOnlyOneConcurrentRotationOfTheSameRefreshToken()
            throws Exception {
        JsonNode tokens = localLogin();
        String refreshToken = tokens.path("refreshToken").asText();
        String tokenHash = cryptoService.sha256(refreshToken);
        List<HttpResponse<String>> responses;
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            PostgresLockContention.lockRefreshToken(blocker, tokenHash);
            responses =
                    ConcurrentTestRequests.run(
                            2,
                            () ->
                                    post(
                                            "/api/v1/auth/refresh",
                                            objectMapper.writeValueAsString(
                                                    Map.of(
                                                            "refreshToken",
                                                            refreshToken)),
                                            null,
                                            null,
                                            null),
                            () -> {
                                assertThat(
                                                PostgresLockContention
                                                        .awaitDatabaseLockWaiters(
                                                                jdbcTemplate,
                                                                1,
                                                                CONTENTION_TIMEOUT))
                                        .isTrue();
                                blocker.commit();
                            },
                            Duration.ofSeconds(10));
        }
        assertThat(responses).filteredOn(response -> response.statusCode() == 200)
                .hasSize(1);
        assertThat(responses).filteredOn(response -> response.statusCode() == 401)
                .allSatisfy(
                        response ->
                                assertThat(
                                                json(response.body())
                                                        .path("code")
                                                        .asText())
                                        .isEqualTo("AUTH_REFRESH_REUSED"))
                .hasSize(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                SELECT count(*)
                                FROM refresh_tokens
                                WHERE family_id = (
                                    SELECT family_id
                                    FROM refresh_tokens
                                    WHERE token_hash = ?
                                )
                                """,
                                Long.class,
                                tokenHash))
                .isEqualTo(2L);
    }

    @Test
    void returnsTheSameOrderForConcurrentIdempotentCreation() throws Exception {
        String accessToken = localLogin().path("accessToken").asText();
        String idempotencyKey = "concurrent-order-" + UUID.randomUUID();
        UUID userId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT user_id
                        FROM user_identities
                        WHERE provider = 'DEVELOPER'
                          AND provider_subject = 'local-debug-developer'
                        """,
                        UUID.class);
        List<HttpResponse<String>> responses;
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            PostgresLockContention.lockAdvisoryKey(
                    blocker, userId + ":" + idempotencyKey);
            responses =
                    ConcurrentTestRequests.run(
                            2,
                            () ->
                                    post(
                                            "/api/v1/payments/orders",
                                            """
                                            {"productCode":"LOCAL_COIN_PACK_1","provider":"MOCK"}
                                            """,
                                            accessToken,
                                            "Idempotency-Key",
                                            idempotencyKey),
                            () -> {
                                assertThat(
                                                PostgresLockContention
                                                        .awaitDatabaseLockWaiters(
                                                                jdbcTemplate,
                                                                2,
                                                                CONTENTION_TIMEOUT))
                                        .isTrue();
                                blocker.commit();
                            },
                            Duration.ofSeconds(10));
        }
        assertThat(responses)
                .allSatisfy(
                        response ->
                                assertThat(response.statusCode()).isEqualTo(200));
        assertThat(responses.stream()
                        .map(HttpResponse::body)
                        .map(this::readOrderId)
                        .distinct())
                .hasSize(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                SELECT count(*)
                                FROM payment_orders
                                WHERE idempotency_key = ?
                                """,
                                Long.class,
                                idempotencyKey))
                .isEqualTo(1L);
    }

    @Test
    void handlesConcurrentDuplicateWebhookWithoutDuplicateOutbox()
            throws Exception {
        String accessToken = localLogin().path("accessToken").asText();
        JsonNode firstOrder = createOrder(accessToken);
        UUID firstOrderId = UUID.fromString(firstOrder.path("id").asText());
        String eventId = "concurrent-event-" + UUID.randomUUID();
        String callbackBody = callbackBody(firstOrder, eventId);
        List<String> callbackBodies =
                List.of(callbackBody, callbackBody);
        List<String> signatures =
                callbackBodies.stream()
                        .map(
                                body ->
                                        cryptoService.hmacSha256(
                                                "local-mock-payment-secret", body))
                        .toList();
        AtomicInteger requestIndex = new AtomicInteger();

        try {
            try (Connection blocker = dataSource.getConnection()) {
                blocker.setAutoCommit(false);
                PostgresLockContention.lockAdvisoryKey(
                        blocker, "MOCK:" + eventId);
                List<HttpResponse<String>> responses =
                        ConcurrentTestRequests.run(
                                2,
                                () -> {
                                    int index = requestIndex.getAndIncrement();
                                    return post(
                                                "/api/v1/payments/webhooks/mock",
                                                callbackBodies.get(index),
                                                null,
                                                "X-Mock-Signature",
                                                signatures.get(index));
                                },
                                () -> {
                                    assertThat(
                                                    PostgresLockContention
                                                            .awaitDatabaseLockWaiters(
                                                                    jdbcTemplate,
                                                                    2,
                                                                    CONTENTION_TIMEOUT))
                                            .isTrue();
                                    blocker.commit();
                                },
                                Duration.ofSeconds(10));

                assertThat(responses)
                        .allSatisfy(
                                response ->
                                        assertThat(response.statusCode())
                                                .isEqualTo(200));
                assertThat(responses.stream()
                                .map(HttpResponse::body)
                                .map(this::readDuplicate)
                                .filter(Boolean::booleanValue))
                        .hasSize(1);
            }
            assertThat(
                            jdbcTemplate.queryForObject(
                                    """
                                    SELECT count(*)
                                    FROM payment_webhook_events
                                    WHERE provider = 'MOCK'
                                      AND provider_event_id = ?
                                    """,
                                    Long.class,
                                    eventId))
                    .isEqualTo(1L);
            assertThat(
                            jdbcTemplate.queryForObject(
                                    """
                                    SELECT count(*)
                                    FROM payment_outbox
                                    WHERE aggregate_id = ?
                                    """,
                                    Long.class,
                                    firstOrderId))
                    .isEqualTo(1L);
        } finally {
            jdbcTemplate.update(
                    "DELETE FROM payment_outbox WHERE aggregate_id = ?",
                    firstOrderId);
            jdbcTemplate.update(
                    """
                    DELETE FROM payment_webhook_events
                    WHERE provider = 'MOCK' AND provider_event_id = ?
                    """,
                    eventId);
            jdbcTemplate.update(
                    "DELETE FROM payment_orders WHERE id = ?",
                    firstOrderId);
        }
    }

    @Test
    void rejectsReusedWebhookEventIdWhenTheSignedPayloadChanges()
            throws Exception {
        String accessToken = localLogin().path("accessToken").asText();
        JsonNode firstOrder = createOrder(accessToken);
        JsonNode secondOrder = createOrder(accessToken);
        UUID firstOrderId = UUID.fromString(firstOrder.path("id").asText());
        UUID secondOrderId = UUID.fromString(secondOrder.path("id").asText());
        String eventId = "reused-event-" + UUID.randomUUID();
        String firstBody = callbackBody(firstOrder, eventId);
        String secondBody = callbackBody(secondOrder, eventId);

        try {
            HttpResponse<String> accepted =
                    post(
                            "/api/v1/payments/webhooks/mock",
                            firstBody,
                            null,
                            "X-Mock-Signature",
                            cryptoService.hmacSha256(
                                    "local-mock-payment-secret", firstBody));
            assertThat(accepted.statusCode()).isEqualTo(200);

            HttpResponse<String> rejected =
                    post(
                            "/api/v1/payments/webhooks/mock",
                            secondBody,
                            null,
                            "X-Mock-Signature",
                            cryptoService.hmacSha256(
                                    "local-mock-payment-secret", secondBody));

            assertThat(rejected.statusCode()).isEqualTo(401);
            assertThat(json(rejected.body()).path("code").asText())
                    .isEqualTo("PAYMENT_CALLBACK_INVALID");
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT status FROM payment_orders WHERE id = ?",
                                    String.class,
                                    secondOrderId))
                    .isEqualTo("PENDING");
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT count(*) FROM payment_outbox WHERE aggregate_id IN (?, ?)",
                                    Long.class,
                                    firstOrderId,
                                    secondOrderId))
                    .isEqualTo(1L);
        } finally {
            jdbcTemplate.update(
                    "DELETE FROM payment_outbox WHERE aggregate_id IN (?, ?)",
                    firstOrderId,
                    secondOrderId);
            jdbcTemplate.update(
                    "DELETE FROM payment_webhook_events WHERE provider = 'MOCK' AND provider_event_id = ?",
                    eventId);
            jdbcTemplate.update(
                    "DELETE FROM payment_orders WHERE id IN (?, ?)",
                    firstOrderId,
                    secondOrderId);
        }
    }

    private JsonNode localLogin() throws Exception {
        HttpResponse<String> response =
                post("/api/v1/auth/debug", "{}", null, null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        return json(response.body());
    }

    private JsonNode createOrder(String accessToken) throws Exception {
        HttpResponse<String> response =
                post(
                        "/api/v1/payments/orders",
                        """
                        {"productCode":"LOCAL_COIN_PACK_1","provider":"MOCK"}
                        """,
                        accessToken,
                        "Idempotency-Key",
                        "webhook-order-" + UUID.randomUUID());
        assertThat(response.statusCode()).isEqualTo(200);
        return json(response.body());
    }

    private String callbackBody(JsonNode order, String eventId) throws Exception {
        return objectMapper.writeValueAsString(
                Map.of(
                        "eventId", eventId,
                        "merchantOrderNo", order.path("merchantOrderNo").asText(),
                        "providerOrderNo", order.path("providerOrderNo").asText(),
                        "amountMinor", order.path("amountMinor").asLong(),
                        "currency", order.path("currency").asText(),
                        "status", "PAID"));
    }

    private UUID readOrderId(String body) {
        try {
            return UUID.fromString(json(body).path("id").asText());
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid order response", exception);
        }
    }

    private boolean readDuplicate(String body) {
        try {
            return json(body).path("duplicate").asBoolean();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid webhook response", exception);
        }
    }
}
