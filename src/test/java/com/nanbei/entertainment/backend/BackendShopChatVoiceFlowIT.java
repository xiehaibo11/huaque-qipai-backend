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
class BackendShopChatVoiceFlowIT extends BackendFlowTestSupport {
    @Test
    void exposesAndExchangesTheOriginalChatVoiceSection() throws Exception {
        String phoneNumber = "13800138204";
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
        assertThat(findProduct(catalog, "INTERACTION_THUMB").path("section").asText())
                .isEqualTo("prop_emoji");
        JsonNode voicePack = findProduct(catalog, "CHAT_VOICE_XIAOGU_1_DAY");
        assertThat(voicePack.path("section").asText()).isEqualTo("yuyin");
        assertThat(voicePack.path("displayName").asText()).isEqualTo("小谷专属语音包1天");
        assertThat(voicePack.path("priceCurrency").asText()).isEqualTo("DIAMOND");
        assertThat(voicePack.path("priceAmount").asLong()).isEqualTo(100);

        JsonNode exchange =
                json(
                        post(
                                        "/api/v1/shop/exchanges",
                                        "{\"productCode\":\"CHAT_VOICE_XIAOGU_1_DAY\"}",
                                        accessToken,
                                        "Idempotency-Key",
                                        "shop-chat-voice-" + UUID.randomUUID())
                                .body());
        assertThat(exchange.path("wallet").path("diamonds").asLong()).isEqualTo(900);

        JsonNode inventory = json(get("/api/v1/shop/inventory", accessToken).body());
        assertThat(inventory.toString())
                .contains("\"itemCode\":\"PROP_CHAT_VOICE_120404\",\"quantity\":1");
    }

    private static JsonNode findProduct(JsonNode catalog, String productCode) {
        for (JsonNode product : catalog.path("products")) {
            if (productCode.equals(product.path("productCode").asText())) {
                return product;
            }
        }
        throw new AssertionError("Missing shop product " + productCode);
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
                "select user_id from user_identities "
                        + "where provider = 'PHONE' and provider_subject = ?",
                UUID.class,
                phoneNumber);
    }
}
