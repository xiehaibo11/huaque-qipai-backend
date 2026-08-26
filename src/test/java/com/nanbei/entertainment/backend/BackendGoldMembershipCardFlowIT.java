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
class BackendGoldMembershipCardFlowIT extends BackendFlowTestSupport {
    @Test
    void purchaseActivatesCardAndDailyClaimCreditsRealWallet() throws Exception {
        String phoneNumber = "13800138204";
        String accessToken = login(phoneNumber);
        UUID userId = userIdByPhone(phoneNumber);
        jdbcTemplate.update(
                """
                insert into player_wallets (
                    user_id, room_card_centi, bound_room_cards, coins, diamonds,
                    coupons, updated_at, version
                ) values (?, 0, 0, 0, 10000, 0, now(), 0)
                on conflict (user_id) do update
                    set coins = 0, diamonds = 10000, updated_at = now()
                """,
                userId);

        assertThat(get("/api/v1/membership/gold-cards", null).statusCode())
                .isEqualTo(401);
        JsonNode initial =
                json(get("/api/v1/membership/gold-cards", accessToken).body());
        assertThat(initial.path("cards").size()).isEqualTo(2);
        assertCard(
                initial.path("cards").get(0),
                "GOLD_MEMBER_WEEK",
                "金币周卡",
                7,
                10_000,
                "NOT_ACTIVE");
        assertCard(
                initial.path("cards").get(1),
                "GOLD_MEMBER_MONTH",
                "金币月卡",
                30,
                15_000,
                "NOT_ACTIVE");
        HttpResponse<String> inactiveClaim =
                post(
                        "/api/v1/membership/gold-cards/GOLD_MEMBER_WEEK/claim",
                        "{}",
                        accessToken,
                        null,
                        null);
        assertThat(inactiveClaim.statusCode()).isEqualTo(403);
        assertThat(json(inactiveClaim.body()).path("code").asText())
                .isEqualTo("GOLD_MEMBERSHIP_NOT_ACTIVE");

        JsonNode exchange =
                json(
                        post(
                                        "/api/v1/shop/exchanges",
                                        "{\"productCode\":\"GOLD_MEMBER_WEEK\"}",
                                        accessToken,
                                        "Idempotency-Key",
                                        "gold-card-week-" + UUID.randomUUID())
                                .body());
        assertThat(exchange.path("wallet").path("diamonds").asLong())
                .isEqualTo(8_200);
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                select count(*) from shop_inventory_items
                                where user_id = ? and item_code = 'GOLD_MEMBER_WEEK'
                                """,
                                Long.class,
                                userId))
                .isZero();

        JsonNode active =
                findCard(
                        json(get("/api/v1/membership/gold-cards", accessToken).body()),
                        "GOLD_MEMBER_WEEK");
        assertThat(active.path("state").asText()).isEqualTo("NOT_AWARD");
        assertThat(active.path("remainingSeconds").asLong())
                .isBetween(7L * 86_400L - 30L, 7L * 86_400L);

        HttpResponse<String> claimResponse =
                post(
                        "/api/v1/membership/gold-cards/GOLD_MEMBER_WEEK/claim",
                        "{}",
                        accessToken,
                        null,
                        null);
        assertThat(claimResponse.statusCode()).isEqualTo(200);
        JsonNode claimed = json(claimResponse.body());
        assertThat(claimed.path("state").asText()).isEqualTo("HAS_AWARD");
        assertThat(walletValue(userId, "coins")).isEqualTo(10_000);

        HttpResponse<String> duplicate =
                post(
                        "/api/v1/membership/gold-cards/GOLD_MEMBER_WEEK/claim",
                        "{}",
                        accessToken,
                        null,
                        null);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(json(duplicate.body()).path("code").asText())
                .isEqualTo("GOLD_MEMBERSHIP_ALREADY_CLAIMED");
        assertThat(walletValue(userId, "coins")).isEqualTo(10_000);
    }

    @Test
    void renewalExtendsCurrentExpiryAndCardsCanBeClaimedIndependently()
            throws Exception {
        String phoneNumber = "13800138205";
        String accessToken = login(phoneNumber);
        UUID userId = userIdByPhone(phoneNumber);
        jdbcTemplate.update(
                """
                insert into player_wallets (
                    user_id, room_card_centi, bound_room_cards, coins, diamonds,
                    coupons, updated_at, version
                ) values (?, 0, 0, 0, 10000, 0, now(), 0)
                on conflict (user_id) do update
                    set coins = 0, diamonds = 10000, updated_at = now()
                """,
                userId);

        exchange(accessToken, "GOLD_MEMBER_WEEK");
        exchange(accessToken, "GOLD_MEMBER_WEEK");
        exchange(accessToken, "GOLD_MEMBER_MONTH");

        Long extendedDays =
                jdbcTemplate.queryForObject(
                        """
                        select extract(epoch from (expires_at - now()))::bigint
                        from gold_membership_cards
                        where user_id = ? and product_code = 'GOLD_MEMBER_WEEK'
                        """,
                        Long.class,
                        userId);
        assertThat(extendedDays).isBetween(14L * 86_400L - 30L, 14L * 86_400L);

        assertThat(
                        post(
                                        "/api/v1/membership/gold-cards/GOLD_MEMBER_WEEK/claim",
                                        "{}",
                                        accessToken,
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(200);
        assertThat(
                        post(
                                        "/api/v1/membership/gold-cards/GOLD_MEMBER_MONTH/claim",
                                        "{}",
                                        accessToken,
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(200);
        assertThat(walletValue(userId, "coins")).isEqualTo(25_000);
    }

    private void exchange(String accessToken, String productCode) throws Exception {
        assertThat(
                        post(
                                        "/api/v1/shop/exchanges",
                                        "{\"productCode\":\"" + productCode + "\"}",
                                        accessToken,
                                        "Idempotency-Key",
                                        "gold-card-" + UUID.randomUUID())
                                .statusCode())
                .isEqualTo(200);
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
        return tokens.path("accessToken").asText();
    }

    private UUID userIdByPhone(String phoneNumber) {
        return jdbcTemplate.queryForObject(
                """
                select user_id from user_identities
                where provider = 'PHONE' and provider_subject = ?
                """,
                UUID.class,
                phoneNumber);
    }

    private long walletValue(UUID userId, String column) {
        return jdbcTemplate.queryForObject(
                "select " + column + " from player_wallets where user_id = ?",
                Long.class,
                userId);
    }

    private static JsonNode findCard(JsonNode response, String productCode) {
        for (JsonNode card : response.path("cards")) {
            if (productCode.equals(card.path("productCode").asText())) {
                return card;
            }
        }
        throw new AssertionError("Missing card " + productCode);
    }

    private static void assertCard(
            JsonNode card,
            String productCode,
            String title,
            int durationDays,
            long dailyCoins,
            String state) {
        assertThat(card.path("productCode").asText()).isEqualTo(productCode);
        assertThat(card.path("title").asText()).isEqualTo(title);
        assertThat(card.path("durationDays").asInt()).isEqualTo(durationDays);
        assertThat(card.path("dailyCoins").asLong()).isEqualTo(dailyCoins);
        assertThat(card.path("state").asText()).isEqualTo(state);
        assertThat(card.path("remainingSeconds").asLong()).isZero();
    }
}
