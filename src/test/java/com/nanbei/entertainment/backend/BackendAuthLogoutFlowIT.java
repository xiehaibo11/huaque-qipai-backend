package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Import(BackendFlowTestcontainersConfiguration.class)
class BackendAuthLogoutFlowIT extends BackendFlowTestSupport {
    @Test
    void logoutRevokesRefreshAndImmediatelyInvalidatesAccessToken()
            throws Exception {
        String phone = "13800138021";
        post(
                "/api/v1/auth/otp/request",
                "{\"phoneNumber\":\"" + phone + "\"}",
                null,
                null,
                null);
        JsonNode tokens =
                json(
                        post(
                                        "/api/v1/auth/otp/verify",
                                        "{\"phoneNumber\":\""
                                                + phone
                                                + "\",\"code\":\"246810\"}",
                                        null,
                                        null,
                                        null)
                                .body());
        String accessToken = tokens.path("accessToken").asText();
        String refreshToken = tokens.path("refreshToken").asText();

        HttpResponse<String> logout =
                post(
                        "/api/v1/auth/logout",
                        "{\"refreshToken\":\"" + refreshToken + "\"}",
                        null,
                        null,
                        null);

        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(get("/api/v1/users/me", accessToken).statusCode())
                .isEqualTo(401);
        assertThat(
                        post(
                                        "/api/v1/auth/refresh",
                                        "{\"refreshToken\":\""
                                                + refreshToken
                                                + "\"}",
                                        null,
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(401);
    }
}
