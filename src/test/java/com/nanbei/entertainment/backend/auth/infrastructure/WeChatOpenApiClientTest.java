package com.nanbei.entertainment.backend.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nanbei.entertainment.backend.common.config.WeChatProperties;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class WeChatOpenApiClientTest {
    @Test
    void exchangesCodeAtFixedWechatEndpoint() {
        TestHarness harness = harness();
        harness.server()
                .expect(
                        requestTo(
                                "https://api.weixin.qq.com/sns/oauth2/access_token"
                                        + "?appid=wx-test"
                                        + "&secret=server-secret"
                                        + "&code=one-time-code"
                                        + "&grant_type=authorization_code"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "access_token": "wechat-access-token",
                                  "expires_in": 7200,
                                  "refresh_token": "wechat-refresh-token",
                                  "openid": "openid-1",
                                  "scope": "snsapi_userinfo",
                                  "unionid": "unionid-1"
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        WeChatTokenResponse response =
                harness.client().exchange("one-time-code");

        assertThat(response.openid()).isEqualTo("openid-1");
        assertThat(response.unionid()).isEqualTo("unionid-1");
        harness.server().verify();
    }

    @Test
    void mapsInvalidOrUsedCodeToInvalidCredential() {
        TestHarness harness = harness();
        harness.server()
                .expect(requestTo(org.hamcrest.Matchers.containsString("code=used-code")))
                .andRespond(
                        withSuccess(
                                """
                                {"errcode":40163,"errmsg":"code been used"}
                                """,
                                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> harness.client().exchange("used-code"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.code())
                                    .isEqualTo(
                                            ErrorCode.AUTH_INVALID_CREDENTIAL);
                            assertThat(exception.getMessage())
                                    .doesNotContain(
                                            "used-code",
                                            "server-secret",
                                            "code been used");
                        });
        harness.server().verify();
    }

    @Test
    void mapsHttpFailureToSafeUpstreamError(CapturedOutput output) {
        TestHarness harness = harness();
        harness.server()
                .expect(requestTo(org.hamcrest.Matchers.containsString("code=temporary-code")))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> harness.client().exchange("temporary-code"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.code())
                                    .isEqualTo(
                                            ErrorCode.AUTH_PROVIDER_UPSTREAM_FAILED);
                            assertThat(exception.getMessage())
                                    .doesNotContain(
                                            "temporary-code", "server-secret");
                        });
        assertThat(output)
                .contains("WeChat OAuth token exchange HTTP failure, status=502")
                .doesNotContain("temporary-code", "server-secret");
        harness.server().verify();
    }

    @Test
    void exchangesTextPlainWechatResponse(
            CapturedOutput output) {
        TestHarness harness = harness();
        harness.server()
                .expect(requestTo(org.hamcrest.Matchers.containsString("code=one-time-code")))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "access_token": "wechat-access-token",
                                  "openid": "openid-1"
                                }
                                """,
                                MediaType.TEXT_PLAIN));

        WeChatTokenResponse response =
                harness.client().exchange("one-time-code");

        assertThat(response.openid()).isEqualTo("openid-1");
        assertThat(output).doesNotContain(
                        "one-time-code",
                        "server-secret",
                        "wechat-access-token",
                        "openid-1");
        harness.server().verify();
    }

    @Test
    void logsOnlyProviderErrorCodeForSafeProductionDiagnosis(
            CapturedOutput output) {
        TestHarness harness = harness();
        harness.server()
                .expect(requestTo(org.hamcrest.Matchers.containsString("code=one-time-code")))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "errcode": 48001,
                                  "errmsg": "provider-detail-must-not-be-logged"
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> harness.client().exchange("one-time-code"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.code())
                                        .isEqualTo(
                                                ErrorCode.AUTH_PROVIDER_UPSTREAM_FAILED));

        assertThat(output)
                .contains("WeChat OAuth token exchange rejected, errcode=48001")
                .doesNotContain(
                        "one-time-code",
                        "server-secret",
                        "provider-detail-must-not-be-logged");
        harness.server().verify();
    }

    private static TestHarness harness() {
        RestClient.Builder builder =
                RestClient.builder().baseUrl("https://api.weixin.qq.com");
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        WeChatOpenApiClient client =
                new WeChatOpenApiClient(
                        properties(),
                        builder.build());
        return new TestHarness(client, server);
    }

    private static WeChatProperties properties() {
        return new WeChatProperties(
                true,
                "wx-test",
                "server-secret",
                Duration.ofSeconds(3),
                Duration.ofSeconds(5));
    }

    private record TestHarness(
            WeChatOpenApiClient client, MockRestServiceServer server) {
    }
}
