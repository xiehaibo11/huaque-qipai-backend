package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Import(BackendFlowTestcontainersConfiguration.class)
class BackendMembershipDailyGiftFlowIT extends BackendFlowTestSupport {
    @Test
    void exposesOriginalDailyGiftOptionsAndRequiresMembershipToClaim()
            throws Exception {
        String phoneNumber = "13800138011";
        String accessToken = login(phoneNumber);

        assertThat(get("/api/v1/membership/daily-gift", null).statusCode())
                .isEqualTo(401);

        HttpResponse<String> statusResponse =
                get("/api/v1/membership/daily-gift", accessToken);
        assertThat(statusResponse.statusCode()).isEqualTo(200);
        JsonNode status = json(statusResponse.body());
        assertThat(status.path("membershipActive").asBoolean()).isFalse();
        assertThat(status.path("claimedToday").asBoolean()).isFalse();
        assertThat(status.path("options").size()).isEqualTo(2);
        assertThat(status.path("options").get(0).path("giftId").asInt())
                .isEqualTo(1);
        assertThat(status.path("options").get(0).path("rewards").get(0).path("displayName").asText())
                .isEqualTo("金币");
        assertThat(status.path("options").get(1).path("rewards").get(0).path("iconKey").asText())
                .isEqualTo("membership_reward_shuffle_ticket");
        assertThat(status.path("options").get(1).path("rewards").get(1).path("displayName").asText())
                .isEqualTo("聚宝盆");

        HttpResponse<String> rejected =
                post(
                        "/api/v1/membership/daily-gift/claim",
                        "{\"giftId\":1}",
                        accessToken,
                        null,
                        null);
        assertThat(rejected.statusCode()).isEqualTo(403);
        assertThat(json(rejected.body()).path("code").asText())
                .isEqualTo("MEMBERSHIP_DAILY_GIFT_NOT_AVAILABLE");
    }

    @Test
    void memberCanClaimOneGiftPerUtcDayAndReceivesCoins() throws Exception {
        String phoneNumber = "13800138012";
        String accessToken = login(phoneNumber);
        UUID userId = userIdByPhone(phoneNumber);
        activateMembership(userId);

        HttpResponse<String> claimed =
                post(
                        "/api/v1/membership/daily-gift/claim",
                        "{\"giftId\":1}",
                        accessToken,
                        null,
                        null);
        assertThat(claimed.statusCode()).isEqualTo(200);
        JsonNode claim = json(claimed.body());
        assertThat(claim.path("membershipActive").asBoolean()).isTrue();
        assertThat(claim.path("claimedToday").asBoolean()).isTrue();
        assertThat(claim.path("claimedGiftId").asInt()).isEqualTo(1);
        assertThat(claim.path("wallet").path("coins").asLong()).isEqualTo(10_000L);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select coins from player_wallets where user_id = ?",
                                Long.class,
                                userId))
                .isEqualTo(10_000L);
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                select count(*)
                                from membership_reward_grants
                                where user_id = ? and source_type = 'MEMBERSHIP_DAILY_GIFT'
                                """,
                                Long.class,
                                userId))
                .isEqualTo(2L);

        JsonNode refreshed = json(get("/api/v1/membership/daily-gift", accessToken).body());
        assertThat(refreshed.path("claimedToday").asBoolean()).isTrue();
        assertThat(refreshed.path("claimedGiftId").asInt()).isEqualTo(1);

        HttpResponse<String> duplicate =
                post(
                        "/api/v1/membership/daily-gift/claim",
                        "{\"giftId\":2}",
                        accessToken,
                        null,
                        null);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(json(duplicate.body()).path("code").asText())
                .isEqualTo("MEMBERSHIP_DAILY_GIFT_ALREADY_CLAIMED");
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

    private UUID userIdByPhone(String phoneNumber) {
        return jdbcTemplate.queryForObject(
                "select user_id from user_identities where provider = 'PHONE' and provider_subject = ?",
                UUID.class,
                phoneNumber);
    }

    private void activateMembership(UUID userId) {
        jdbcTemplate.update(
                "insert into player_profiles "
                        + "(user_id, public_player_id, avatar_key, membership_level, created_at, updated_at) "
                        + "values (?, nextval('public_player_id_seq'), 'avatar_default', 1, now(), now()) "
                        + "on conflict (user_id) do update set membership_level = 1, updated_at = now()",
                userId);
        jdbcTemplate.update(
                """
                insert into user_memberships (
                    user_id, membership_level, started_at, expires_at, auto_renew,
                    last_order_id, created_at, updated_at
                ) values (?, 1, now(), now() + interval '30 days', false, null, now(), now())
                on conflict (user_id) do update set
                    membership_level = 1,
                    started_at = excluded.started_at,
                    expires_at = excluded.expires_at,
                    auto_renew = false,
                    updated_at = now()
                """,
                userId);
    }
}
