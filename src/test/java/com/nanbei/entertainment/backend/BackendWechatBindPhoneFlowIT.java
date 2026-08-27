package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Import(BackendFlowTestcontainersConfiguration.class)
class BackendWechatBindPhoneFlowIT extends BackendFlowTestSupport {
    @Test
    void wechatUserCanBindPhoneAndLoginBackWithThatPhone() throws Exception {
        String phoneNumber = "138" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100_000_000);

        JsonNode wechatTokens =
                json(
                        post(
                                        "/api/v1/auth/providers/wechat/login",
                                        "{\"credential\":\"wechat-bind-phone-" + UUID.randomUUID() + "\"}",
                                        null,
                                        null,
                                        null)
                                .body());
        String wechatAccessToken = wechatTokens.path("accessToken").asText();
        String wechatUserId =
                json(get("/api/v1/users/me", wechatAccessToken).body()).path("id").asText();

        JsonNode beforeBind =
                json(get("/api/v1/personal-center", wechatAccessToken).body());
        assertThat(beforeBind.path("account").path("phoneBound").asBoolean()).isFalse();

        assertThat(
                        post(
                                        "/api/v1/personal-center/phone/code",
                                        "{\"phoneNumber\":\"" + phoneNumber + "\"}",
                                        wechatAccessToken,
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(200);
        JsonNode bindResult =
                json(
                        put(
                                        "/api/v1/personal-center/phone",
                                        "{\"phoneNumber\":\"" + phoneNumber + "\",\"code\":\"246810\"}",
                                        wechatAccessToken)
                                .body());
        assertThat(bindResult.path("maskedPhone").asText())
                .isEqualTo(phoneNumber.substring(0, 3) + "****" + phoneNumber.substring(7));
        assertThat(bindResult.path("reloginRequired").asBoolean()).isFalse();

        JsonNode afterBind =
                json(get("/api/v1/personal-center", wechatAccessToken).body());
        assertThat(afterBind.path("account").path("phoneBound").asBoolean()).isTrue();

        insertLoginOtpChallenge(phoneNumber, "246810");
        JsonNode phoneTokens =
                json(
                        post(
                                        "/api/v1/auth/otp/verify",
                                        "{\"phoneNumber\":\"" + phoneNumber + "\",\"code\":\"246810\"}",
                                        null,
                                        null,
                                        null)
                                .body());
        String phoneAccessToken = phoneTokens.path("accessToken").asText();
        String phoneUserId =
                json(get("/api/v1/users/me", phoneAccessToken).body()).path("id").asText();

        assertThat(phoneUserId).isEqualTo(wechatUserId);
    }

    private void insertLoginOtpChallenge(String phoneNumber, String code) {
        jdbcTemplate.update(
                """
                insert into otp_challenges (
                    id, phone_number, purpose, code_hash, expires_at,
                    attempts, max_attempts, consumed_at, created_at
                ) values (?, ?, 'LOGIN', ?, now() + interval '5 minutes',
                    0, 5, null, now())
                """,
                UUID.randomUUID(),
                phoneNumber,
                cryptoService.sha256(phoneNumber + ":" + code));
    }
}
