package com.nanbei.entertainment.backend.realname.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nanbei.entertainment.backend.common.config.RealNameProperties;
import com.nanbei.entertainment.backend.realname.application.RealNameVerifyResult;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class AliyunRealNameVerifierTest {

    private HttpServer server;
    private String endpoint;
    private AtomicReference<String> lastQuery;
    private AtomicReference<String> lastAuthorization;

    @BeforeEach
    void startServer() throws IOException {
        lastQuery = new AtomicReference<>();
        lastAuthorization = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        endpoint =
                "http://127.0.0.1:" + server.getAddress().getPort() + "/id/check";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void returnsMatchWhenIsokTrue() {
        respond(
                200,
                "{\"error_code\":0,\"reason\":\"Success\",\"result\":"
                        + "{\"isok\":true}}");

        RealNameVerifyResult result = verifier().verify("张三", "110101199001011237");

        assertThat(result).isEqualTo(RealNameVerifyResult.MATCH);
        assertThat(lastQuery.get()).contains("cardNo=110101199001011237");
        assertThat(lastQuery.get()).contains("realName=");
        assertThat(lastAuthorization.get()).isEqualTo("APPCODE test-appcode");
    }

    @Test
    void returnsMismatchWhenIsokFalse() {
        respond(
                200,
                "{\"error_code\":0,\"reason\":\"Success\",\"result\":"
                        + "{\"isok\":false}}");

        assertThat(verifier().verify("张三", "110101199001011237"))
                .isEqualTo(RealNameVerifyResult.MISMATCH);
    }

    /**
     * 一次干净的 isok=false 以前不写任何日志，运维只能看到客户端的"姓名与身份证号不一致"，
     * 无法区分"上游判定不一致"和"根本没走到上游"。
     */
    @Test
    void logsTheUpstreamDecisionSoMismatchesAreDiagnosable() {
        respond(
                200,
                "{\"error_code\":0,\"reason\":\"Success\",\"result\":"
                        + "{\"isok\":false}}");
        ListAppender<ILoggingEvent> appender = attachAppender();

        verifier().verify("张三", "110101199001011237");

        assertThat(renderedMessages(appender))
                .anySatisfy(
                        message ->
                                assertThat(message)
                                        .contains("Real-name verification completed")
                                        .contains("matched=false"));
    }

    @Test
    void logsTheUpstreamDecisionOnMatchToo() {
        respond(
                200,
                "{\"error_code\":0,\"reason\":\"Success\",\"result\":"
                        + "{\"isok\":true}}");
        ListAppender<ILoggingEvent> appender = attachAppender();

        verifier().verify("张三", "110101199001011237");

        assertThat(renderedMessages(appender))
                .anySatisfy(message -> assertThat(message).contains("matched=true"));
    }

    /** 日志绝不能带上姓名或证件号，这是该适配器的既有隐私约定。 */
    @Test
    void neverLogsTheNameOrIdCardNumber() {
        respond(
                200,
                "{\"error_code\":0,\"reason\":\"Success\",\"result\":"
                        + "{\"isok\":false}}");
        ListAppender<ILoggingEvent> appender = attachAppender();

        verifier().verify("张三", "110101199001011237");

        assertThat(renderedMessages(appender))
                .allSatisfy(
                        message ->
                                assertThat(message)
                                        .doesNotContain("张三")
                                        .doesNotContain("110101199001011237"));
    }

    private ListAppender<ILoggingEvent> attachAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(AliyunRealNameVerifier.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static List<String> renderedMessages(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void returnsUnavailableWhenErrorCodeNonZero() {
        respond(200, "{\"error_code\":10028,\"reason\":\"缺少参数\",\"result\":null}");

        assertThat(verifier().verify("张三", "110101199001011237"))
                .isEqualTo(RealNameVerifyResult.UNAVAILABLE);
    }

    @Test
    void returnsUnavailableOnHttpError() {
        respond(403, "Unauthorized");

        assertThat(verifier().verify("张三", "110101199001011237"))
                .isEqualTo(RealNameVerifyResult.UNAVAILABLE);
    }

    @Test
    void returnsUnavailableOnInvalidJson() {
        respond(200, "not-json");

        assertThat(verifier().verify("张三", "110101199001011237"))
                .isEqualTo(RealNameVerifyResult.UNAVAILABLE);
    }

    private AliyunRealNameVerifier verifier() {
        RealNameProperties properties =
                new RealNameProperties(
                        true, endpoint, "test-appcode", "test-secret", 5);
        return new AliyunRealNameVerifier(
                properties, buildClient(), new ObjectMapper());
    }

    private void respond(int status, String body) {
        server.createContext(
                "/id/check",
                exchange -> {
                    lastQuery.set(exchange.getRequestURI().getRawQuery());
                    lastAuthorization.set(
                            exchange.getRequestHeaders()
                                    .getFirst("Authorization"));
                    byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(status, payload.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(payload);
                    }
                });
        server.start();
    }

    private static RestClient buildClient() {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
