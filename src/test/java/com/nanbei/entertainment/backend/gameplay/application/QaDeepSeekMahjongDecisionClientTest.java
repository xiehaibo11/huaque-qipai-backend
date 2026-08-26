package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class QaDeepSeekMahjongDecisionClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private URI baseUri;
    private AtomicInteger requests;
    private AtomicReference<String> method;
    private AtomicReference<String> authorization;
    private AtomicReference<String> requestBody;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        requests = new AtomicInteger();
        method = new AtomicReference<>();
        authorization = new AtomicReference<>();
        requestBody = new AtomicReference<>();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void postsAJsonOnlyNonStreamingDecisionRequestAndReturnsTheActionId() throws Exception {
        respond(200, "{\"choices\":[{\"message\":{\"content\":\"{\\\"actionId\\\":\\\"discard:17\\\"}\"}}]}");

        assertThat(client(true, "test-key").choose("seat 2 may discard", List.of("discard:17")))
                .contains("discard:17");

        assertThat(method.get()).isEqualTo("POST");
        assertThat(authorization.get()).isEqualTo("Bearer test-key");
        assertThat(requests.get()).isEqualTo(1);
        var body = objectMapper.readTree(requestBody.get());
        assertThat(body.path("model").asText()).isEqualTo("deepseek-v4-flash");
        assertThat(body.path("max_tokens").asInt()).isEqualTo(64);
        assertThat(body.path("stream").asBoolean()).isFalse();
        assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(body.path("thinking").path("type").asText()).isEqualTo("disabled");
        assertThat(body.path("messages").get(0).path("content").asText())
                .contains("台州麻将")
                .contains("牌型")
                .contains("河牌")
                .contains("副露")
                .contains("胜率最高")
                .contains("合法 actionId")
                .contains("JSON only");
        assertThat(body.path("messages").get(1).path("content").asText())
                .contains("seat 2 may discard")
                .contains("discard:17");
    }

    @Test
    void makesNoRequestWhenDisabledOrTheApiKeyIsBlank() {
        respond(200, "{\"choices\":[{\"message\":{\"content\":\"{\\\"actionId\\\":\\\"discard:17\\\"}\"}}]}");

        assertThat(client(false, "test-key").choose("state", List.of("discard:17"))).isEmpty();
        assertThat(client(true, " ").choose("state", List.of("discard:17"))).isEmpty();

        assertThat(requests.get()).isZero();
    }

    @Test
    void returnsEmptyForAnUpstreamHttpError() {
        respond(503, "unavailable");
        QaDeepSeekMahjongDecisionClient client = client(true, "test-key");

        assertThat(client.choose("state", List.of("discard:17"))).isEmpty();
        assertThat(client.choose("state", List.of("discard:17"))).isEmpty();
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void capsSuccessfulRequestsWithinOneEngineDecisionBudget() {
        respond(200, "{\"choices\":[{\"message\":{\"content\":\"{\\\"actionId\\\":\\\"discard:17\\\"}\"}}]}");
        QaDeepSeekMahjongDecisionClient client = client(true, "test-key");

        assertThat(client.choose("state", List.of("discard:17"))).contains("discard:17");
        assertThat(client.choose("state", List.of("discard:17"))).contains("discard:17");
        assertThat(client.choose("state", List.of("discard:17"))).contains("discard:17");
        assertThat(client.choose("state", List.of("discard:17"))).isEmpty();
        assertThat(requests.get()).isEqualTo(3);
    }

    @Test
    void returnsEmptyForEmptyOrMalformedDecisionResponses() {
        respond(200, "{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}");

        assertThat(client(true, "test-key").choose("state", List.of("discard:17"))).isEmpty();
    }

    @Test
    void returnsEmptyWhenTheDecisionRequestTimesOut() {
        server.createContext(
                "/chat/completions",
                exchange -> {
                    requests.incrementAndGet();
                    try {
                        Thread.sleep(300);
                        exchange.sendResponseHeaders(200, -1);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } finally {
                        exchange.close();
                    }
                });
        server.start();

        assertThat(client(true, "test-key", Duration.ofMillis(50))
                        .choose("state", List.of("discard:17")))
                .isEmpty();
    }

    private QaDeepSeekMahjongDecisionClient client(boolean enabled, String apiKey) {
        return client(enabled, apiKey, Duration.ofSeconds(1));
    }

    private QaDeepSeekMahjongDecisionClient client(
            boolean enabled, String apiKey, Duration timeout) {
        return new QaDeepSeekMahjongDecisionClient(
                enabled,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                baseUri,
                apiKey,
                "deepseek-v4-flash",
                timeout);
    }

    private void respond(int status, String body) {
        server.createContext(
                "/chat/completions",
                exchange -> {
                    requests.incrementAndGet();
                    method.set(exchange.getRequestMethod());
                    authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                    requestBody.set(
                            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(status, payload.length);
                    try (OutputStream output = exchange.getResponseBody()) {
                        output.write(payload);
                    }
                });
        server.start();
    }
}
