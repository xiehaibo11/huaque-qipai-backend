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
class BackendMembershipNoticeFlowIT extends BackendFlowTestSupport {
    @Test
    void requiresAuthenticationAndReturnsTheVersionedOriginalNotice() throws Exception {
        assertThat(get("/api/v1/membership/notice", null).statusCode())
                .isEqualTo(401);

        String accessToken = login("13800138118");
        HttpResponse<String> response =
                get("/api/v1/membership/notice", accessToken);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode notice = json(response.body());
        assertThat(notice.path("version").asInt()).isEqualTo(1);
        assertThat(notice.path("title").asText()).isEqualTo("会员须知");
        assertThat(notice.path("items").size()).isEqualTo(4);
        assertThat(notice.path("items").get(0).asText())
                .isEqualTo("1.购买会员卡后可立即获得对应会员，并在会员卡时效内获得对应权益。");
        assertThat(notice.path("items").get(1).asText())
                .isEqualTo("2.每日领取福利需要在【会员权益】界面手动领取，未领取的福利次日失效。");
        assertThat(notice.path("items").get(3).asText())
                .isEqualTo("4.续费或叠加购买同种会员卡，时效自动顺延。");
        assertThat(notice.path("changeNotice").asText())
                .startsWith("如在您会员卡生效期间，因运营策略调整等原因发生变更");
        assertThat(notice.path("agreementTitle").asText()).isEqualTo("用户协议");
        assertThat(notice.path("agreementUrl").asText())
                .isEqualTo("https://www.nanbeiyule.com/terms");
        assertThat(notice.path("updatedAt").asText()).isNotBlank();
    }

    private String login(String phoneNumber) throws Exception {
        assertThat(
                        post(
                                        "/api/v1/auth/otp/request",
                                        "{\"phoneNumber\":\"" + phoneNumber + "\"}",
                                        null,
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(202);
        return json(
                        post(
                                        "/api/v1/auth/otp/verify",
                                        "{\"phoneNumber\":\""
                                                + phoneNumber
                                                + "\",\"code\":\"246810\"}",
                                        null,
                                        null,
                                        null)
                                .body())
                .path("accessToken")
                .asText();
    }
}
