package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Import(BackendFlowTestcontainersConfiguration.class)
class BackendRealNameFlowIT extends BackendFlowTestSupport {
    private static final String LOCAL_ID_CARD = "110101199001011237";

    @Test
    void completesRealNameVerificationLifecycle() throws Exception {
        String accessToken = login("13800138001");

        JsonNode unverified =
                json(get("/api/v1/real-name/status", accessToken).body());
        assertThat(unverified.path("status").asText())
                .isEqualTo("UNVERIFIED");
        assertThat(unverified.has("realNameMasked")).isFalse();
        assertThat(unverified.path("alipayOneTapEnabled").asBoolean())
                .isFalse();

        HttpResponse<String> verified =
                post(
                        "/api/v1/real-name/verify",
                        "{\"realName\":\"张测试\",\"idCardNumber\":\""
                                + LOCAL_ID_CARD
                                + "\"}",
                        accessToken,
                        null,
                        null);
        assertThat(verified.statusCode()).isEqualTo(200);
        JsonNode snapshot = json(verified.body());
        assertThat(snapshot.path("status").asText()).isEqualTo("VERIFIED");
        assertThat(snapshot.path("realNameMasked").asText())
                .isEqualTo("张**");
        assertThat(snapshot.path("idCardMasked").asText())
                .isEqualTo("1101**********1237");
        assertThat(snapshot.path("verifiedAt").asText()).isNotBlank();

        JsonNode status =
                json(get("/api/v1/real-name/status", accessToken).body());
        assertThat(status.path("status").asText()).isEqualTo("VERIFIED");
        assertThat(status.path("realNameMasked").asText())
                .isEqualTo("张**");
        assertThat(status.path("idCardMasked").asText())
                .isEqualTo("1101**********1237");
        assertThat(status.path("verifiedAt").asText()).isNotBlank();

        HttpResponse<String> repeated =
                post(
                        "/api/v1/real-name/verify",
                        "{\"realName\":\"张测试\",\"idCardNumber\":\""
                                + LOCAL_ID_CARD
                                + "\"}",
                        accessToken,
                        null,
                        null);
        assertThat(repeated.statusCode()).isEqualTo(200);
        assertThat(json(repeated.body()).path("realNameMasked").asText())
                .isEqualTo("张**");
    }

    @Test
    void reportsMismatchForUnknownIdCardNumbers() throws Exception {
        String accessToken = login("13800138002");

        HttpResponse<String> mismatch =
                post(
                        "/api/v1/real-name/verify",
                        "{\"realName\":\"张测试\",\"idCardNumber\":\"110101199202024755\"}",
                        accessToken,
                        null,
                        null);
        assertThat(mismatch.statusCode()).isEqualTo(400);
        assertThat(json(mismatch.body()).path("code").asText())
                .isEqualTo("REALNAME_MISMATCH");

        JsonNode status =
                json(get("/api/v1/real-name/status", accessToken).body());
        assertThat(status.path("status").asText())
                .isEqualTo("UNVERIFIED");
    }

    @Test
    void rejectsInvalidIdCardFormatAndMissingToken() throws Exception {
        String accessToken = login("13800138003");

        HttpResponse<String> invalid =
                post(
                        "/api/v1/real-name/verify",
                        "{\"realName\":\"张测试\",\"idCardNumber\":\"110101199001011238\"}",
                        accessToken,
                        null,
                        null);
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(json(invalid.body()).path("code").asText())
                .isEqualTo("REALNAME_INVALID_FORMAT");

        assertThat(get("/api/v1/real-name/status", null).statusCode())
                .isEqualTo(401);
        assertThat(
                        post(
                                        "/api/v1/real-name/verify",
                                        "{\"realName\":\"张测试\",\"idCardNumber\":\""
                                                + LOCAL_ID_CARD
                                                + "\"}",
                                        null,
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(401);
    }

    private String login(String phoneNumber) throws Exception {
        HttpResponse<String> otpResponse =
                post(
                        "/api/v1/auth/otp/request",
                        "{\"phoneNumber\":\"" + phoneNumber + "\"}",
                        null,
                        null,
                        null);
        assertThat(otpResponse.statusCode()).isEqualTo(202);

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
        String accessToken = tokens.path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        return accessToken;
    }
}
