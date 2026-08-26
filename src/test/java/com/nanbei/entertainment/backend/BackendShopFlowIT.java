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
        assertThat(catalog.path("products").size()).isEqualTo(90);
        assertThat(catalog.path("products").get(0).path("productCode").asText())
                .isEqualTo("SXVIP_CONTINUOUS_MONTH");
        assertThat(catalog.path("products").toString())
                .contains(
                        "\"productCode\":\"SXVIP_7_DAYS\"",
                        "\"productCode\":\"PROP_RECORDER_2_HOURS\"",
                        "\"productCode\":\"GOLD_GIFT_6\"",
                        "\"section\":\"gold_gift\"",
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

        JsonNode goldGift =
                json(
                        post(
                                        "/api/v1/payments/orders",
                                        "{\"productCode\":\"GOLD_GIFT_6\",\"provider\":\"MOCK\"}",
                                        accessToken,
                                        "Idempotency-Key",
                                        "shop-gold-gift-" + UUID.randomUUID())
                                .body());
        long coinsBeforeGoldGift = walletValue(userId, "coins");
        pay(goldGift);
        assertThat(walletValue(userId, "coins")).isEqualTo(coinsBeforeGoldGift + 78_000);

        HttpResponse<String> dailyBenefit =
                post(
                        "/api/v1/shop/exchanges",
                        "{\"productCode\":\"HOT_DAILY_BENEFIT\"}",
                        accessToken,
                        "Idempotency-Key",
                        "shop-daily-benefit-" + UUID.randomUUID());
        assertThat(dailyBenefit.statusCode()).isEqualTo(200);
        JsonNode refreshedCatalog = json(get("/api/v1/shop/catalog", accessToken).body());
        JsonNode refreshedDailyBenefit =
                findProduct(refreshedCatalog, "HOT_DAILY_BENEFIT");
        assertThat(refreshedDailyBenefit.path("purchasedToday").asLong()).isEqualTo(1);
        assertThat(refreshedDailyBenefit.path("remainingPurchases").asInt()).isZero();
    }

    @Test
    void exchangesWashCardsAndLuckBeadsIntoSharedInventoryStacks() throws Exception {
        String phoneNumber = "13800138202";
        String accessToken = login(phoneNumber);
        UUID userId = userIdByPhone(phoneNumber);
        jdbcTemplate.update(
                """
                insert into player_wallets (
                    user_id, room_card_centi, bound_room_cards, coins, diamonds,
                    coupons, updated_at, version
                ) values (?, 0, 0, 0, 1000, 0, now(), 0)
                on conflict (user_id) do update
                    set diamonds = 1000, updated_at = now()
                """,
                userId);

        JsonNode catalog = json(get("/api/v1/shop/catalog", accessToken).body());
        assertThat(findProduct(catalog, "PROP_WASH_CARD_5").path("section").asText())
                .isEqualTo("wash_card");
        assertThat(findProduct(catalog, "PROP_LUCK_BEAD_10").path("section").asText())
                .isEqualTo("luck_prop");

        JsonNode washCards =
                json(
                        post(
                                        "/api/v1/shop/exchanges",
                                        "{\"productCode\":\"PROP_WASH_CARD_5\"}",
                                        accessToken,
                                        "Idempotency-Key",
                                        "shop-wash-card-" + UUID.randomUUID())
                                .body());
        assertThat(washCards.path("wallet").path("diamonds").asLong()).isEqualTo(910);

        JsonNode luckBeads =
                json(
                        post(
                                        "/api/v1/shop/exchanges",
                                        "{\"productCode\":\"PROP_LUCK_BEAD_10\"}",
                                        accessToken,
                                        "Idempotency-Key",
                                        "shop-luck-bead-" + UUID.randomUUID())
                                .body());
        assertThat(luckBeads.path("wallet").path("diamonds").asLong()).isEqualTo(750);

        JsonNode inventory = json(get("/api/v1/shop/inventory", accessToken).body());
        assertThat(inventory.toString())
                .contains(
                        "\"itemCode\":\"PROP_WASH_CARD\",\"quantity\":5",
                        "\"itemCode\":\"PROP_LUCK_BEAD\",\"quantity\":10");
    }

    @Test
    void classifiesAndExchangesEveryDecorationSection() throws Exception {
        String phoneNumber = "13800138203";
        String accessToken = login(phoneNumber);
        UUID userId = userIdByPhone(phoneNumber);
        jdbcTemplate.update(
                """
                insert into player_wallets (
                    user_id, room_card_centi, bound_room_cards, coins, diamonds,
                    coupons, updated_at, version
                ) values (?, 0, 0, 0, 20000, 0, now(), 0)
                on conflict (user_id) do update
                    set diamonds = 20000, updated_at = now()
                """,
                userId);

        JsonNode catalog = json(get("/api/v1/shop/catalog", accessToken).body());
        assertThat(findProduct(catalog, "DECORATION_VEHICLE_150801").path("section").asText())
                .isEqualTo("enterani");
        assertThat(findProduct(catalog, "DECORATION_VEHICLE_150816").path("displayName").asText())
                .isEqualTo("越野家7天");
        assertThat(findProduct(catalog, "DECORATION_TABLE_1").path("section").asText())
                .isEqualTo("tablebg");
        assertThat(findProduct(catalog, "DECORATION_TABLE_3").path("section").asText())
                .isEqualTo("pb");
        assertThat(findProduct(catalog, "DECORATION_TABLE_6").path("section").asText())
                .isEqualTo("txk");
        assertThat(findProduct(catalog, "DECORATION_TABLE_9").path("section").asText())
                .isEqualTo("ypq");
        assertThat(findProduct(catalog, "DECORATION_TABLE_1").path("displayName").asText())
                .isEqualTo("财神桌布7天");
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                select count(*)
                                from shop_products shop
                                left join payment_products payment
                                  on payment.id = shop.payment_product_id
                                where shop.enabled
                                  and (
                                    shop.price_currency not in (
                                      'CNY', 'DIAMOND', 'ROOM_CARD', 'COUPON', 'FREE'
                                    )
                                    or (shop.price_currency = 'CNY' and (
                                      payment.id is null
                                      or not payment.enabled
                                      or payment.currency <> shop.price_currency
                                      or payment.amount_minor <> shop.price_amount
                                    ))
                                    or (shop.price_currency <> 'CNY'
                                      and shop.payment_product_id is not null)
                                  )
                                """,
                                Long.class))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                select count(*)
                                from shop_products shop
                                where shop.enabled
                                  and not exists (
                                    select 1 from shop_product_rewards reward
                                    where reward.product_id = shop.id
                                  )
                                """,
                                Long.class))
                .isZero();

        String[] vehicleCodes = {
            "DECORATION_VEHICLE_150801",
            "DECORATION_VEHICLE_150802",
            "DECORATION_VEHICLE_150804",
            "DECORATION_VEHICLE_150803",
            "DECORATION_VEHICLE_150808",
            "DECORATION_VEHICLE_150807",
            "DECORATION_VEHICLE_150806",
            "DECORATION_VEHICLE_150805",
            "DECORATION_VEHICLE_150816"
        };
        for (String vehicleCode : vehicleCodes) {
            exchange(accessToken, vehicleCode);
        }
        exchange(accessToken, "DECORATION_TABLE_1");
        exchange(accessToken, "DECORATION_TABLE_3");
        exchange(accessToken, "DECORATION_TABLE_6");
        JsonNode lastExchange = exchange(accessToken, "DECORATION_TABLE_9");
        assertThat(lastExchange.path("wallet").path("diamonds").asLong()).isEqualTo(2900);

        JsonNode inventory = json(get("/api/v1/shop/inventory", accessToken).body());
        assertThat(inventory.toString())
                .contains(
                        "\"itemCode\":\"DECORATION_TABLE_1\",\"quantity\":7",
                        "\"itemCode\":\"DECORATION_TABLE_3\",\"quantity\":7",
                        "\"itemCode\":\"DECORATION_TABLE_6\",\"quantity\":7",
                        "\"itemCode\":\"DECORATION_TABLE_9\",\"quantity\":7",
                        "\"itemCode\":\"PROP_RQDH_150801\",\"quantity\":7",
                        "\"itemCode\":\"PROP_RQDH_150816\",\"quantity\":7");
    }

    private JsonNode exchange(String accessToken, String productCode) throws Exception {
        HttpResponse<String> response =
                post(
                        "/api/v1/shop/exchanges",
                        "{\"productCode\":\"" + productCode + "\"}",
                        accessToken,
                        "Idempotency-Key",
                        "shop-decoration-" + productCode + "-" + UUID.randomUUID());
        assertThat(response.statusCode()).isEqualTo(200);
        return json(response.body());
    }

    private static JsonNode findProduct(JsonNode catalog, String productCode) {
        for (JsonNode product : catalog.path("products")) {
            if (productCode.equals(product.path("productCode").asText())) {
                return product;
            }
        }
        throw new AssertionError("missing shop product " + productCode);
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
