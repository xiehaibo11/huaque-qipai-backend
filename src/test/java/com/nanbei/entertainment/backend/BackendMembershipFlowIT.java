package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Import(BackendFlowTestcontainersConfiguration.class)
class BackendMembershipFlowIT extends BackendFlowTestSupport {
    @Test
    void createsPendingOrdersForEveryOriginalSxvipProduct() throws Exception {
        String accessToken = login("13800138100");
        Map<String, Long> products =
                Map.of(
                        "SXVIP_CONTINUOUS_MONTH", 2_800L,
                        "SXVIP_30_DAYS", 3_500L,
                        "SXVIP_90_DAYS", 7_800L,
                        "SXVIP_365_DAYS", 26_800L,
                        "SXVIP_7_DAYS", 2_500L);

        for (Map.Entry<String, Long> product : products.entrySet()) {
            HttpResponse<String> response =
                    post(
                            "/api/v1/payments/orders",
                            "{\"productCode\":\""
                                    + product.getKey()
                                    + "\",\"provider\":\"MOCK\"}",
                            accessToken,
                            "Idempotency-Key",
                            "all-sxvip-" + product.getKey() + "-" + UUID.randomUUID());

            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode order = json(response.body());
            assertThat(order.path("status").asText()).isEqualTo("PENDING");
            assertThat(order.path("amountMinor").asLong())
                    .isEqualTo(product.getValue());
        }
    }

    @Test
    void exposesOriginalSxvipProductsAndActivatesMembershipOnlyAfterPaidWebhook()
            throws Exception {
        String phoneNumber = "13800138101";
        String accessToken = login(phoneNumber);
        UUID userId = userIdByPhone(phoneNumber);

        assertThat(get("/api/v1/membership/products", null).statusCode())
                .isEqualTo(401);

        JsonNode products =
                json(get("/api/v1/membership/products", accessToken).body());
        assertThat(products.size()).isEqualTo(5);
        JsonNode first = products.get(0);
        assertThat(first.path("productCode").asText())
                .isEqualTo("SXVIP_CONTINUOUS_MONTH");
        assertThat(first.path("name").asText()).isEqualTo("30天会员");
        assertThat(first.path("durationDays").asInt()).isEqualTo(30);
        assertThat(first.path("amountMinor").asLong()).isEqualTo(2_800L);
        assertThat(first.path("priceText").asText()).isEqualTo("连续包月:28元");
        assertThat(first.path("giftValueYuan").asInt()).isEqualTo(42);
        assertThat(first.path("subscription").asBoolean()).isTrue();
        assertThat(first.path("rewards").size()).isEqualTo(6);
        assertThat(first.path("rewards").get(0).path("displayName").asText())
                .isEqualTo("金币");
        assertThat(first.path("rewards").get(1).path("iconKey").asText())
                .isEqualTo("membership_reward_shuffle_ticket");
        assertThat(first.path("rewards").get(5).path("iconKey").asText())
                .isEqualTo("membership_reward_card_back");
        assertThat(products.get(3).path("productCode").asText())
                .isEqualTo("SXVIP_365_DAYS");
        assertThat(products.get(3).path("cardStyle").asText())
                .isEqualTo("PURPLE");
        assertThat(products.get(4).path("productCode").asText())
                .isEqualTo("SXVIP_7_DAYS");
        assertThat(products.get(4).path("rewards").get(1).path("iconKey").asText())
                .isEqualTo("membership_reward_shuffle_ticket");
        assertThat(products.get(4).path("rewards").get(3).path("iconKey").asText())
                .isEqualTo("membership_reward_card_back");

        JsonNode statusBefore =
                json(get("/api/v1/membership/status", accessToken).body());
        assertThat(statusBefore.path("membershipActive").asBoolean()).isFalse();

        JsonNode order =
                json(
                        post(
                                        "/api/v1/payments/orders",
                                        """
                                        {"productCode":"SXVIP_7_DAYS","provider":"MOCK"}
                                        """,
                                        accessToken,
                                        "Idempotency-Key",
                                        "sxvip-order-" + UUID.randomUUID())
                                .body());
        assertThat(order.path("status").asText()).isEqualTo("PENDING");
        assertThat(order.path("amountMinor").asLong()).isEqualTo(2_500L);
        assertThat(json(get("/api/v1/membership/status", accessToken).body())
                        .path("membershipActive")
                        .asBoolean())
                .isFalse();

        String callbackBody =
                """
                {
                  "eventId": "%s",
                  "merchantOrderNo": "%s",
                  "providerOrderNo": "%s",
                  "amountMinor": %d,
                  "currency": "CNY",
                  "status": "PAID"
                }
                """
                        .formatted(
                                "sxvip-paid-" + UUID.randomUUID(),
                                order.path("merchantOrderNo").asText(),
                                order.path("providerOrderNo").asText(),
                                order.path("amountMinor").asLong());
        HttpResponse<String> webhook =
                post(
                        "/api/v1/payments/webhooks/mock",
                        callbackBody,
                        null,
                        "X-Mock-Signature",
                        cryptoService.hmacSha256(
                                "local-mock-payment-secret", callbackBody));
        assertThat(webhook.statusCode()).isEqualTo(200);

        JsonNode statusAfter =
                json(get("/api/v1/membership/status", accessToken).body());
        assertThat(statusAfter.path("membershipActive").asBoolean()).isTrue();
        assertThat(statusAfter.path("membershipLevel").asInt()).isEqualTo(1);
        assertThat(statusAfter.path("remainingDays").asLong())
                .isBetween(6L, 7L);
        assertThat(statusAfter.path("expiresAt").asText()).isNotBlank();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from user_memberships where user_id = ? and expires_at > now()",
                                Long.class,
                                userId))
                .isEqualTo(1L);
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
                                where user_id = ? and source_type = 'MEMBERSHIP_PURCHASE'
                                """,
                                Long.class,
                                userId))
                .isEqualTo(4L);

        HttpResponse<String> claimed =
                post(
                        "/api/v1/membership/daily-gift/claim",
                        "{\"giftId\":1}",
                        accessToken,
                        null,
                        null);
        assertThat(claimed.statusCode()).isEqualTo(200);
    }

    @Test
    void expiredMembershipCannotClaimDailyGiftEvenWhenLegacyProfileFlagIsStale()
            throws Exception {
        String phoneNumber = "13800138102";
        String accessToken = login(phoneNumber);
        UUID userId = userIdByPhone(phoneNumber);
        jdbcTemplate.update(
                """
                insert into player_profiles (
                    user_id, public_player_id, avatar_key, membership_level, created_at, updated_at
                ) values (?, nextval('public_player_id_seq'), 'avatar_default', 1, now(), now())
                on conflict (user_id) do update set membership_level = 1, updated_at = now()
                """,
                userId);
        jdbcTemplate.update(
                """
                insert into user_memberships (
                    user_id, membership_level, started_at, expires_at, auto_renew,
                    last_order_id, created_at, updated_at
                ) values (?, 1, now() - interval '40 days', now() - interval '1 day',
                    false, null, now(), now())
                """,
                userId);

        JsonNode status = json(get("/api/v1/membership/daily-gift", accessToken).body());
        assertThat(status.path("membershipActive").asBoolean()).isFalse();

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
}
