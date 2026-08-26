package com.nanbei.entertainment.backend.wechatsubscription.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nanbei.entertainment.backend.common.config.WeChatProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class WeChatAppAccessTokenProviderTest {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-25T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void fetchesExactClientCredentialUrlAndCachesToken() {
        Harness harness = harness();
        harness.server()
                .expect(
                        requestTo(
                                "https://api.weixin.qq.com/cgi-bin/token"
                                        + "?grant_type=client_credential"
                                        + "&appid=wx-test&secret=server-secret"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withSuccess(
                                """
                                {"access_token":"app-access-token","expires_in":7200}
                                """,
                                MediaType.APPLICATION_JSON));

        assertThat(harness.provider().getToken()).isEqualTo("app-access-token");
        assertThat(harness.provider().getToken()).isEqualTo("app-access-token");
        harness.server().verify();
    }

    @Test
    void concurrentCallersShareOneTokenFetch() throws Exception {
        Harness harness = harness();
        harness.server()
                .expect(requestTo(org.hamcrest.Matchers.containsString("/cgi-bin/token")))
                .andRespond(
                        withSuccess(
                                """
                                {"access_token":"shared-token","expires_in":7200}
                                """,
                                MediaType.APPLICATION_JSON));
        Callable<String> call = harness.provider()::getToken;
        List<Future<String>> futures = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(20)) {
            for (int index = 0; index < 20; index++) {
                futures.add(executor.submit(call));
            }
            for (Future<String> future : futures) {
                assertThat(future.get()).isEqualTo("shared-token");
            }
        }
        harness.server().verify();
    }

    @Test
    void rejectsProviderErrorWithoutLeakingProviderMessage() {
        Harness harness = harness();
        harness.server()
                .expect(requestTo(org.hamcrest.Matchers.containsString("/cgi-bin/token")))
                .andRespond(
                        withSuccess(
                                """
                                {"errcode":40013,"errmsg":"invalid appid with secret"}
                                """,
                                MediaType.APPLICATION_JSON));

        assertThatThrownBy(harness.provider()::getToken)
                .isInstanceOf(WeChatSubscriptionProviderException.class)
                .hasMessageNotContaining("server-secret")
                .hasMessageNotContaining("invalid appid");
        harness.server().verify();
    }

    private static Harness harness() {
        RestClient.Builder builder =
                RestClient.builder().baseUrl("https://api.weixin.qq.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WeChatAppAccessTokenProvider provider =
                new WeChatAppAccessTokenProvider(
                        properties(), builder.build(), new ObjectMapper(), CLOCK);
        return new Harness(provider, server);
    }

    private static WeChatProperties properties() {
        return new WeChatProperties(
                true,
                "wx-test",
                "server-secret",
                Duration.ofSeconds(3),
                Duration.ofSeconds(5));
    }

    private record Harness(
            WeChatAppAccessTokenProvider provider,
            MockRestServiceServer server) {}
}
