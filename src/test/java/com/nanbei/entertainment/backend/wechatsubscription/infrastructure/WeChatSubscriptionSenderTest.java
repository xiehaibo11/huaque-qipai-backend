package com.nanbei.entertainment.backend.wechatsubscription.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nanbei.entertainment.backend.common.config.WeChatProperties;
import com.nanbei.entertainment.backend.common.config.WeChatSubscriptionProperties;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class WeChatSubscriptionSenderTest {
    @Mock WeChatAppAccessTokenProvider tokenProvider;

    @Test
    void postsExactOfficialPathAndPayload() {
        Harness harness = harness();
        when(tokenProvider.getToken()).thenReturn("app-token");
        harness.server()
                .expect(
                        requestTo(
                                "https://api.weixin.qq.com/cgi-bin/message/template/subscribe"
                                        + "?access_token=app-token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(
                        content()
                                .json(
                                        """
                                        {
                                          "touser":"openid-1",
                                          "template_id":"%s",
                                          "scene":1000,
                                          "title":"系统通知",
                                          "url":"https://example.invalid/result",
                                          "data":{"content":{"value":"真实业务事件已完成","color":"#173177"}}
                                        }
                                        """
                                                .formatted(
                                                        WeChatSubscriptionProperties.TEMPLATE_ID),
                                        true))
                .andRespond(
                        withSuccess(
                                "{\"errcode\":0,\"errmsg\":\"ok\"}",
                                MediaType.APPLICATION_JSON));

        WeChatSubscriptionSendResult result = harness.sender().send(message());

        assertThat(result.status()).isEqualTo(WeChatSubscriptionSendStatus.SENT);
        harness.server().verify();
    }

    @Test
    void invalidTokenRefreshesAndRetriesExactlyOnce() {
        Harness harness = harness();
        when(tokenProvider.getToken()).thenReturn("old-token", "new-token");
        harness.server()
                .expect(requestTo(org.hamcrest.Matchers.containsString("old-token")))
                .andRespond(
                        withSuccess(
                                "{\"errcode\":40014,\"errmsg\":\"invalid token\"}",
                                MediaType.APPLICATION_JSON));
        harness.server()
                .expect(requestTo(org.hamcrest.Matchers.containsString("new-token")))
                .andRespond(
                        withSuccess(
                                "{\"errcode\":0,\"errmsg\":\"ok\"}",
                                MediaType.APPLICATION_JSON));

        WeChatSubscriptionSendResult result = harness.sender().send(message());

        assertThat(result.status()).isEqualTo(WeChatSubscriptionSendStatus.SENT);
        verify(tokenProvider).invalidate("old-token");
        verify(tokenProvider, times(2)).getToken();
        harness.server().verify();
    }

    @Test
    void unknownProviderErrorIsTerminal() {
        Harness harness = harness();
        when(tokenProvider.getToken()).thenReturn("app-token");
        harness.server()
                .expect(requestTo(org.hamcrest.Matchers.containsString("app-token")))
                .andRespond(
                        withSuccess(
                                "{\"errcode\":43004,\"errmsg\":\"rejected\"}",
                                MediaType.APPLICATION_JSON));

        WeChatSubscriptionSendResult result = harness.sender().send(message());

        assertThat(result.status())
                .isEqualTo(WeChatSubscriptionSendStatus.TERMINAL);
        assertThat(result.providerCode()).isEqualTo(43004);
    }

    @Test
    void networkOutcomeAfterSendIsAmbiguous() {
        Harness harness = harness();
        when(tokenProvider.getToken()).thenReturn("app-token");
        harness.server()
                .expect(requestTo(org.hamcrest.Matchers.containsString("app-token")))
                .andRespond(withException(new SocketTimeoutException("timeout")));

        WeChatSubscriptionSendResult result = harness.sender().send(message());

        assertThat(result.status())
                .isEqualTo(WeChatSubscriptionSendStatus.AMBIGUOUS);
        assertThat(result.failureClass()).isEqualTo("NETWORK_AMBIGUOUS");
    }

    @Test
    void httpFailureAfterPostingIsAmbiguousAndNeverRetried() {
        Harness harness = harness();
        when(tokenProvider.getToken()).thenReturn("app-token");
        harness.server()
                .expect(requestTo(org.hamcrest.Matchers.containsString("app-token")))
                .andRespond(withServerError());

        WeChatSubscriptionSendResult result = harness.sender().send(message());

        assertThat(result.status())
                .isEqualTo(WeChatSubscriptionSendStatus.AMBIGUOUS);
        verify(tokenProvider).getToken();
        harness.server().verify();
    }

    private Harness harness() {
        RestClient.Builder builder =
                RestClient.builder().baseUrl("https://api.weixin.qq.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WeChatSubscriptionSender sender =
                new WeChatSubscriptionSender(
                        properties(),
                        tokenProvider,
                        builder.build(),
                        new ObjectMapper());
        return new Harness(sender, server);
    }

    private static WeChatSubscriptionMessage message() {
        return new WeChatSubscriptionMessage(
                "openid-1",
                WeChatSubscriptionProperties.TEMPLATE_ID,
                1000,
                "系统通知",
                "真实业务事件已完成",
                "https://example.invalid/result");
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
            WeChatSubscriptionSender sender, MockRestServiceServer server) {}
}
