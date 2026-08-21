package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Import(BackendFlowTestcontainersConfiguration.class)
class BackendMatchArenaFlowIT extends BackendFlowTestSupport {
    @Test
    void serializesConcurrentIdempotentCreationAndEnforcesJuniorLimit()
            throws Exception {
        String accessToken = login("13800138232");
        UUID userId = userIdByPhone("13800138232");
        selectTaizhou(userId);
        String request = creationRequest("888", 0);
        String concurrentKey = "match-arena-concurrent-" + UUID.randomUUID();

        List<HttpResponse<String>> concurrent =
                ConcurrentTestRequests.run(
                        2,
                        () ->
                                post(
                                        "/api/v1/match-arenas",
                                        request,
                                        accessToken,
                                        "Idempotency-Key",
                                        concurrentKey),
                        () -> {},
                        Duration.ofSeconds(10));

        assertThat(concurrent).extracting(HttpResponse::statusCode)
                .containsExactlyInAnyOrder(200, 201);
        List<String> ids =
                List.of(
                        json(concurrent.get(0).body()).path("id").asText(),
                        json(concurrent.get(1).body()).path("id").asText());
        assertThat(ids).doesNotContain("").allMatch(ids.getFirst()::equals);
        assertThat(count("match_arenas", "owner_user_id", userId)).isEqualTo(1);

        HttpResponse<String> second =
                post(
                        "/api/v1/match-arenas",
                        creationRequest("889", 0),
                        accessToken,
                        "Idempotency-Key",
                        "match-arena-second-" + UUID.randomUUID());
        assertThat(second.statusCode()).isEqualTo(201);

        HttpResponse<String> limited =
                post(
                        "/api/v1/match-arenas",
                        creationRequest("890", 0),
                        accessToken,
                        "Idempotency-Key",
                        "match-arena-third-" + UUID.randomUUID());
        assertThat(limited.statusCode()).isEqualTo(409);
        assertThat(json(limited.body()).path("code").asText())
                .isEqualTo("MATCH_ARENA_LIMIT_REACHED");
        assertThat(count("match_arenas", "owner_user_id", userId)).isEqualTo(2);
    }

    @Test
    void createsIdempotentlyPersistsFundingAndReturnsTheNewArenaInMyList()
            throws Exception {
        assertThat(get("/api/v1/match-arenas", null).statusCode()).isEqualTo(401);

        String phoneNumber = "13800138231";
        String accessToken = login(phoneNumber);
        UUID userId = userIdByPhone(phoneNumber);
        selectTaizhou(userId);
        jdbcTemplate.update(
                """
                insert into player_wallets (
                    user_id, room_card_centi, bound_room_cards, coins, diamonds,
                    coupons, updated_at, version
                ) values (?, 1200, 99, 0, 0, 0, now(), 0)
                on conflict (user_id) do update
                    set room_card_centi = 1200, bound_room_cards = 99, updated_at = now()
                """,
                userId);

        String idempotencyKey = "match-arena-" + UUID.randomUUID();
        String request =
                """
                {
                  "lobbyId": 900023,
                  "remark": "888",
                  "level": "LEGACY",
                  "mode": "LEADER",
                  "costType": "CHAMPION",
                  "initialRoomCards": 7,
                  "dailyRoomCardLimit": 888888,
                  "visibleToStrangers": true,
                  "autoTransferEnabled": false,
                  "autoTransferThreshold": 50,
                  "autoTransferAmount": 0,
                  "lowCardReminderThreshold": null
                }
                """;
        HttpResponse<String> missingKey =
                post("/api/v1/match-arenas", request, accessToken, null, null);
        assertThat(missingKey.statusCode()).isEqualTo(400);
        assertThat(json(missingKey.body()).path("code").asText())
                .isEqualTo("VALIDATION_FAILED");

        HttpResponse<String> malformed =
                post(
                        "/api/v1/match-arenas",
                        request.replace("\"LEADER\"", "\"NOT_A_MODE\""),
                        accessToken,
                        "Idempotency-Key",
                        "malformed-" + UUID.randomUUID());
        assertThat(malformed.statusCode()).isEqualTo(400);
        assertThat(json(malformed.body()).path("code").asText())
                .isEqualTo("VALIDATION_FAILED");

        HttpResponse<String> unaffordableAutoTransfer =
                post(
                        "/api/v1/match-arenas",
                        request.replace(
                                        "\"autoTransferEnabled\": false",
                                        "\"autoTransferEnabled\": true")
                                .replace(
                                        "\"autoTransferAmount\": 0",
                                        "\"autoTransferAmount\": 13"),
                        accessToken,
                        "Idempotency-Key",
                        "unaffordable-auto-" + UUID.randomUUID());
        assertThat(unaffordableAutoTransfer.statusCode()).isEqualTo(409);
        assertThat(json(unaffordableAutoTransfer.body()).path("code").asText())
                .isEqualTo("MATCH_ARENA_INSUFFICIENT_ROOM_CARDS");
        assertThat(walletValue(userId, "room_card_centi")).isEqualTo(1200);
        assertThat(count("match_arenas", "owner_user_id", userId)).isZero();

        HttpResponse<String> created =
                post(
                        "/api/v1/match-arenas",
                        request,
                        accessToken,
                        "Idempotency-Key",
                        idempotencyKey);
        assertThat(created.statusCode())
                .withFailMessage("match arena create failed: %s", created.body())
                .isEqualTo(201);
        JsonNode arena = json(created.body());
        assertThat(arena.path("arenaNumber").asText()).matches("\\d{6}");
        assertThat(arena.path("areaName").asText()).isEqualTo("台州");
        assertThat(arena.path("remark").asText()).isEqualTo("888");
        assertThat(arena.path("role").asText()).isEqualTo("OWNER");
        assertThat(arena.path("roomCards").asLong()).isEqualTo(7);
        assertThat(arena.path("duplicate").asBoolean()).isFalse();
        assertThat(created.headers().firstValue("Location")).isEmpty();
        assertThat(count("player_profiles", "user_id", userId)).isEqualTo(1);

        HttpResponse<String> duplicate =
                post(
                        "/api/v1/match-arenas",
                        request,
                        accessToken,
                        "Idempotency-Key",
                        idempotencyKey);
        assertThat(duplicate.statusCode()).isEqualTo(200);
        assertThat(json(duplicate.body()).path("id").asText())
                .isEqualTo(arena.path("id").asText());
        assertThat(json(duplicate.body()).path("duplicate").asBoolean()).isTrue();

        HttpResponse<String> conflict =
                post(
                        "/api/v1/match-arenas",
                        request.replace("\"888\"", "\"777\""),
                        accessToken,
                        "Idempotency-Key",
                        idempotencyKey);
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(json(conflict.body()).path("code").asText())
                .isEqualTo("MATCH_ARENA_IDEMPOTENCY_CONFLICT");

        JsonNode list = json(get("/api/v1/match-arenas", accessToken).body());
        assertThat(list.path("items").size()).isEqualTo(1);
        assertThat(list.path("items").get(0).path("id").asText())
                .isEqualTo(arena.path("id").asText());
        assertThat(walletValue(userId, "room_card_centi")).isEqualTo(500);
        assertThat(walletValue(userId, "bound_room_cards")).isEqualTo(99);
        assertThat(count("match_arenas", "owner_user_id", userId)).isEqualTo(1);
        assertThat(count("match_arena_members", "user_id", userId)).isEqualTo(1);
        assertThat(count("match_arena_card_ledger", "user_id", userId)).isEqualTo(1);
    }

    private String login(String phoneNumber) throws Exception {
        HttpResponse<String> requested =
                post(
                        "/api/v1/auth/otp/request",
                        "{\"phoneNumber\":\"" + phoneNumber + "\"}",
                        null,
                        null,
                        null);
        assertThat(requested.statusCode()).isEqualTo(202);
        HttpResponse<String> verified =
                post(
                        "/api/v1/auth/otp/verify",
                        "{\"phoneNumber\":\""
                                + phoneNumber
                                + "\",\"code\":\"246810\"}",
                        null,
                        null,
                        null);
        assertThat(verified.statusCode()).isEqualTo(200);
        String accessToken = json(verified.body()).path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        return accessToken;
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

    private void selectTaizhou(UUID userId) {
        jdbcTemplate.update(
                """
                insert into user_region_selections (user_id, lobby_id, updated_at)
                values (?, 900023, now())
                on conflict (user_id) do update set lobby_id = 900023, updated_at = now()
                """,
                userId);
    }

    private static String creationRequest(String remark, long initialRoomCards) {
        return """
                {"lobbyId":900023,"remark":"%s","level":"JUNIOR",
                 "mode":"LEADER","costType":"CHAMPION",
                 "initialRoomCards":%d,"dailyRoomCardLimit":888888,
                 "visibleToStrangers":true,"autoTransferEnabled":false,
                 "autoTransferThreshold":50,"autoTransferAmount":0,
                 "lowCardReminderThreshold":null}
                """.formatted(remark, initialRoomCards);
    }

    private long walletValue(UUID userId, String column) {
        return jdbcTemplate.queryForObject(
                "select " + column + " from player_wallets where user_id = ?",
                Long.class,
                userId);
    }

    private long count(String table, String userColumn, UUID userId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where " + userColumn + " = ?",
                Long.class,
                userId);
    }
}
