package com.nanbei.entertainment.backend.payment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class JdkYishoumiTransportTest {
    private HttpServer server;
    private URI endpoint;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        endpoint =
                URI.create(
                        "http://127.0.0.1:"
                                + server.getAddress().getPort()
                                + "/payment");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void postsUtfEightJsonBodyRequiredByOfficialSdk() throws Exception {
        AtomicReference<String> requestMethod = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        respond(
                200,
                "{\"code\":0}",
                requestMethod,
                contentType,
                requestBody);

        String response =
                transport()
                        .postJson(
                                endpoint,
                                Map.of(
                                        "appid", "app-1",
                                        "description", "365天会员",
                                        "payType", 11,
                                        "total", 26_800L));

        assertThat(response).isEqualTo("{\"code\":0}");
        assertThat(requestMethod.get()).isEqualTo("POST");
        assertThat(contentType.get())
                .startsWith("application/json");
        assertThat(
                        new ObjectMapper()
                                .readValue(requestBody.get(), Map.class))
                .containsAllEntriesOf(
                        Map.of(
                                "appid", "app-1",
                                "description", "365天会员",
                                "payType", 11,
                                "total", 26_800));
    }

    @Test
    void rejectsNonSuccessfulHttpResponse() {
        respond(
                502,
                "upstream unavailable",
                new AtomicReference<>(),
                new AtomicReference<>(),
                new AtomicReference<>());

        assertThatThrownBy(
                        () ->
                                transport()
                                        .postJson(
                                                endpoint,
                                                Map.of("appid", "app-1")))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.code())
                                        .isEqualTo(
                                                ErrorCode
                                                        .PAYMENT_PROVIDER_UPSTREAM_FAILED));
    }

    private JdkYishoumiTransport transport() {
        return new JdkYishoumiTransport(
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                new ObjectMapper());
    }

    private void respond(
            int status,
            String body,
            AtomicReference<String> requestMethod,
            AtomicReference<String> contentType,
            AtomicReference<String> requestBody) {
        server.createContext(
                "/payment",
                exchange -> {
                    requestMethod.set(exchange.getRequestMethod());
                    contentType.set(
                            exchange.getRequestHeaders().getFirst("Content-Type"));
                    requestBody.set(
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8));
                    byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(status, payload.length);
                    try (OutputStream output = exchange.getResponseBody()) {
                        output.write(payload);
                    }
                });
        server.start();
    }
}
