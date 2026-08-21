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
class BackendShopFlowIT extends BackendFlowTestSupport {
    @Test
    void exposesNativeCatalogAndCompletesExchangeInventoryAndPaidFulfillment()
            throws Exception {
        assertThat(get("/api/v1/shop/catalog", null).statusCode()).isEqualTo(401);

        String phoneNumber = "13800138201";
        String accessToken = login(phoneNumber);
        UUID userId = userIdByPhone(phoneNumber);
        jdbcTemplate.update(
                """
                insert into player_wallets (
                    user_id, room_card_centi, bound_room_cards, coins, diamonds,
                    coupons, updated_at, version
                ) values (?, 900, 0, 0, 1000, 2000, now(), 0)
                on conflict (user_id) do update
                    set room_card_centi = 900, diamonds = 1000,
                        coupons = 2000, updated_at = now()
                """,
                userId);

        JsonNode catalog = json(get("/api/v1/shop/catalog", accessToken).body());
        assertThat(catalog.path("products").size()).isEqualTo(70);
        assertThat(catalog.path("products").get(0).path("productCode").asText())
                .isEqualTo("SXVIP_CONTINUOUS_MONTH");
        assertThat(catalog.path("products").toString())
                .contains(
                        "\"productCode\":\"SXVIP_7_DAYS\"",
                        "\"productCode\":\"PROP_RECORDER_2_HOURS\"",
                        "\"priceCurrency\":\"ROOM_CARD\"");
        assertThat(catalog.path("wallet").path("roomCards").asLong()).isEqualTo(9);
        assertThat(catalog.path("wallet").path("diamonds").asLong()).isEqualTo(1000);
        assertThat(catalog.path("wallet").path("coupons").asLong()).isEqualTo(2000);
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                select count(*)
                                from shop_products shop
                                join payment_products payment
                                  on payment.id = shop.payment_product_id
                                where shop.product_code = 'SXVIP_7_DAYS'
                                  and payment.product_code = 'SXVIP_7_DAYS'
                                """,
                                Long.class))
                .isEqualTo(1L);

        HttpResponse<String> sevenDayMembershipResponse =
                post(
                        "/api/v1/payments/orders",
                        "{\"productCode\":\"SXVIP_7_DAYS\",\"provider\":\"MOCK\"}",
                        accessToken,
                        "Idempotency-Key",
                        "shop-seven-day-membership-" + UUID.randomUUID());
        assertThat(sevenDayMembershipResponse.statusCode()).isEqualTo(200);
        JsonNode sevenDayMembershipOrder = json(sevenDayMembershipResponse.body());
        assertThat(sevenDayMembershipOrder.path("status").asText()).isEqualTo("PENDING");
        assertThat(sevenDayMembershipOrder.path("amountMinor").asLong()).isEqualTo(2500);

        String roomCardKey = "shop-room-card-" + UUID.randomUUID();
        HttpResponse<String> exchanged =
                post(
                        "/api/v1/shop/exchanges",
                        "{\"productCode\":\"ROOM_CARD_1\"}",
                        accessToken,
                        "Idempotency-Key",
                        roomCardKey);
        assertThat(exchanged.statusCode()).isEqualTo(200);
        JsonNode exchange = json(exchanged.body());
        assertThat(exchange.path("duplicate").asBoolean()).isFalse();
        assertThat(exchange.path("wallet").path("diamonds").asLong()).isEqualTo(600);
        assertThat(exchange.path("wallet").path("roomCards").asLong()).isEqualTo(10);

        JsonNode duplicate =
                json(
                        post(
                                        "/api/v1/shop/exchanges",
                                        "{\"productCode\":\"ROOM_CARD_1\"}",
                                        accessToken,
                                        "Idempotency-Key",
                                        roomCardKey)
                                .body());
        assertThat(duplicate.path("duplicate").asBoolean()).isTrue();
        assertThat(duplicate.path("wallet").path("diamonds").asLong()).isEqualTo(600);
        assertThat(duplicate.path("wallet").path("roomCards").asLong()).isEqualTo(10);

        JsonNode recorderExchange =
                json(
                        post(
                                        "/api/v1/shop/exchanges",
                                        "{\"productCode\":\"PROP_RECORDER_2_HOURS\"}",
                                        accessToken,
                                        "Idempotency-Key",
                                        "shop-recorder-hours-" + UUID.randomUUID())
                                .body());
        assertThat(recorderExchange.path("wallet").path("roomCards").asLong())
                .isEqualTo(7);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select quantity from shop_inventory_items "
                                        + "where user_id = ? and item_code = 'SHOP_RECORDER_MINUTE'",
                                Long.class,
                                userId))
                .isEqualTo(120L);

        HttpResponse<String> bypassedPayment =
                post(
                        "/api/v1/shop/exchanges",
                        "{\"productCode\":\"DIAMOND_100\"}",
                        accessToken,
                        "Idempotency-Key",
                        "shop-payment-bypass-" + UUID.randomUUID());
        assertThat(bypassedPayment.statusCode()).isEqualTo(409);
        assertThat(json(bypassedPayment.body()).path("code").asText())
                .isEqualTo("SHOP_PAYMENT_REQUIRED");

        JsonNode inventoryExchange =
                json(
                        post(
                                        "/api/v1/shop/exchanges",
                                        "{\"productCode\":\"PROP_GOLD_CARD_1\"}",
                                        accessToken,
                                        "Idempotency-Key",
                                        "shop-inventory-" + UUID.randomUUID())
                                .body());
        assertThat(inventoryExchange.path("wallet").path("diamonds").asLong())
                .isZero();
        JsonNode inventory = json(get("/api/v1/shop/inventory", accessToken).body());
        assertThat(inventory.size()).isEqualTo(2);
        assertThat(inventory.toString())
                .contains(
                        "\"itemCode\":\"PROP_GOLD_CARD_1\"",
                        "\"itemCode\":\"SHOP_RECORDER_MINUTE\"");

        JsonNode order =
                json(
                        post(
                                        "/api/v1/payments/orders",
                                        "{\"productCode\":\"DIAMOND_100\",\"provider\":\"MOCK\"}",
                                        accessToken,
                                        "Idempotency-Key",
                                        "shop-paid-order-" + UUID.randomUUID())
                                .body());
        assertThat(order.path("status").asText()).isEqualTo("PENDING");
        assertThat(walletValue(userId, "diamonds")).isZero();

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
                                "shop-paid-" + UUID.randomUUID(),
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
        assertThat(walletValue(userId, "diamonds")).isEqualTo(100);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from shop_purchase_records where order_id = ?",
                                Long.class,
                                UUID.fromString(order.path("id").asText())))
                .isEqualTo(1L);

        JsonNode firstRecharge =
                json(
                        post(
                                        "/api/v1/payments/orders",
                                        "{\"productCode\":\"HOT_FIRST_RECHARGE\",\"provider\":\"MOCK\"}",
                                        accessToken,
                                        "Idempotency-Key",
                                        "shop-first-recharge-" + UUID.randomUUID())
                                .body());
        HttpResponse<String> secondFirstRecharge =
                post(
                        "/api/v1/payments/orders",
                        "{\"productCode\":\"HOT_FIRST_RECHARGE\",\"provider\":\"MOCK\"}",
                        accessToken,
                        "Idempotency-Key",
                        "shop-first-recharge-second-" + UUID.randomUUID());
        assertThat(secondFirstRecharge.statusCode()).isEqualTo(409);
        assertThat(json(secondFirstRecharge.body()).path("code").asText())
                .isEqualTo("SHOP_DAILY_LIMIT_REACHED");

        pay(firstRecharge);
        assertThat(walletValue(userId, "diamonds")).isEqualTo(200);
        assertThat(walletValue(userId, "coins")).isEqualTo(20_000);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select quantity from shop_inventory_items where user_id = ? and item_code = 'SHOP_RECORDER_DAY'",
                                Long.class,
                                userId))
                .isEqualTo(1L);
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

    private UUID userIdByPhone(String phoneNumber) {
        return jdbcTemplate.queryForObject(
                "select user_id from user_identities where provider = 'PHONE' and provider_subject = ?",
                UUID.class,
                phoneNumber);
    }

    private long walletValue(UUID userId, String column) {
        if (!"diamonds".equals(column) && !"coins".equals(column)) {
            throw new IllegalArgumentException("unsupported wallet column");
        }
        return jdbcTemplate.queryForObject(
                "select " + column + " from player_wallets where user_id = ?",
                Long.class,
                userId);
    }

    private void pay(JsonNode order) throws Exception {
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
                                "shop-extra-paid-" + UUID.randomUUID(),
                                order.path("merchantOrderNo").asText(),
                                order.path("providerOrderNo").asText(),
                                order.path("amountMinor").asLong());
        assertThat(
                        post(
                                        "/api/v1/payments/webhooks/mock",
                                        callbackBody,
                                        null,
                                        "X-Mock-Signature",
                                        cryptoService.hmacSha256(
                                                "local-mock-payment-secret", callbackBody))
                                .statusCode())
                .isEqualTo(200);
    }
}
