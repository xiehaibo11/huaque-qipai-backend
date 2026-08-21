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
class BackendRoomLifecycleFlowIT extends RoomFlowTestSupport {
    @Test
    void createsIdempotentlyThenChargesExactlyOnceOnFirstRound() throws Exception {
        String accessToken = loginNewUser();
        UUID ownerId = lastUserId;
        setRoomCardCenti(ownerId, 200);
        String idempotencyKey = "room-" + UUID.randomUUID();

        HttpResponse<String> createdResponse =
                post(
                        "/api/v1/rooms",
                        defaultWulongRequest(),
                        accessToken,
                        "Idempotency-Key",
                        idempotencyKey);
        assertThat(createdResponse.statusCode()).isEqualTo(201);
        JsonNode created = json(createdResponse.body());
        String roomNumber = created.path("roomNumber").asText();
        assertThat(roomNumber).matches("[0-9]{6}");
        assertThat(created.path("status").asText()).isEqualTo("OPEN");
        assertThat(created.path("gameId").asLong()).isEqualTo(30588L);
        assertThat(created.path("playerCount").asInt()).isEqualTo(4);
        assertThat(created.path("playCount").asInt()).isEqualTo(4);
        assertThat(created.path("payType").asText()).isEqualTo("ALL");
        assertThat(created.path("roomFeeCenti").asInt()).isEqualTo(100);
        assertThat(created.path("roomMode").asInt()).isEqualTo(1);
        assertThat(created.path("gameRule").asText())
                .isEqualTo(
                        "FiveHalfDeck='1';CanContinue='1';RankScore='0';"
                                + "LastPlayerScoreToFirst='1';gamezhang='6';"
                                + "BombRewardMultiplier='30';PayType='0';"
                                + "IsSysTrust='15';ShowCardCount='1';LmtMarker='0';"
                                + "ZaoFan='0';UserRule='AutoReady=true;';RoomFee='1';");
        assertThat(roomCardCenti(ownerId)).isEqualTo(200);

        HttpResponse<String> duplicateResponse =
                post(
                        "/api/v1/rooms",
                        defaultWulongRequest(),
                        accessToken,
                        "Idempotency-Key",
                        idempotencyKey);
        assertThat(duplicateResponse.statusCode()).isEqualTo(201);
        assertThat(json(duplicateResponse.body()).path("roomNumber").asText())
                .isEqualTo(roomNumber);

        Login participant = loginUser();
        Login participant2 = loginUser();
        Login participant3 = loginUser();
        for (Login login : java.util.List.of(participant, participant2, participant3)) {
            HttpResponse<String> joined =
                    post(
                            "/api/v1/rooms/" + roomNumber + "/join",
                            "{}",
                            login.accessToken(),
                            null,
                            null);
            assertThat(joined.statusCode()).isEqualTo(200);
        }

        HttpResponse<String> queried =
                get("/api/v1/rooms/" + roomNumber, participant.accessToken());
        assertThat(queried.statusCode()).isEqualTo(200);
        assertThat(json(queried.body()).path("roomRule").asText())
                .isEqualTo(
                        "roomrule={GamePlayerCount=\"4\",group=\"30588\",cancreate=\"1\",roommode=\"10\"}");

        HttpResponse<String> firstRound =
                post(
                        "/api/v1/rooms/" + roomNumber + "/first-round",
                        "{}",
                        accessToken,
                        null,
                        null);
        assertThat(firstRound.statusCode()).isEqualTo(200);
        assertThat(json(firstRound.body()).path("status").asText()).isEqualTo("CHARGED");
        assertThat(roomCardCenti(ownerId)).isEqualTo(100);

        HttpResponse<String> duplicateFirstRound =
                post(
                        "/api/v1/rooms/" + roomNumber + "/first-round",
                        "{}",
                        accessToken,
                        null,
                        null);
        assertThat(duplicateFirstRound.statusCode()).isEqualTo(200);
        assertThat(roomCardCenti(ownerId)).isEqualTo(100);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from room_card_ledger where room_id = (select id from game_rooms where room_number = ?)",
                                Long.class,
                                roomNumber))
                .isEqualTo(1L);

        HttpResponse<String> closedAfterStart =
                post(
                        "/api/v1/rooms/" + roomNumber + "/dissolve",
                        "{}",
                        accessToken,
                        null,
                        null);
        assertThat(closedAfterStart.statusCode()).isEqualTo(200);
        assertThat(json(closedAfterStart.body()).path("status").asText())
                .isEqualTo("DISSOLVED");
        assertThat(roomCardCenti(ownerId)).isEqualTo(100);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from room_card_ledger where room_id = (select id from game_rooms where room_number = ?)",
                                Long.class,
                                roomNumber))
                .isEqualTo(1L);

        HttpResponse<String> nextRoom =
                post(
                        "/api/v1/rooms",
                        defaultWulongRequest(),
                        accessToken,
                        "Idempotency-Key",
                        "room-" + UUID.randomUUID());
        assertThat(nextRoom.statusCode()).isEqualTo(201);
        assertThat(json(nextRoom.body()).path("roomNumber").asText())
                .isNotEqualTo(roomNumber);
    }

    @Test
    void dissolvesAnUnstartedRoomWithoutChargingAndRejectsInvalidCreation() throws Exception {
        String accessToken = loginNewUser();
        UUID ownerId = lastUserId;
        setRoomCardCenti(ownerId, 100);

        JsonNode created =
                json(
                        post(
                                        "/api/v1/rooms",
                                        defaultWulongRequest(),
                                        accessToken,
                                        "Idempotency-Key",
                                        "room-" + UUID.randomUUID())
                                .body());
        String roomNumber = created.path("roomNumber").asText();
        HttpResponse<String> dissolved =
                post(
                        "/api/v1/rooms/" + roomNumber + "/dissolve",
                        "{}",
                        accessToken,
                        null,
                        null);
        assertThat(dissolved.statusCode()).isEqualTo(200);
        assertThat(json(dissolved.body()).path("status").asText()).isEqualTo("DISSOLVED");
        assertThat(roomCardCenti(ownerId)).isEqualTo(100);
        assertThat(
                        post(
                                        "/api/v1/rooms/" + roomNumber + "/dissolve",
                                        "{}",
                                        accessToken,
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(200);

        setRoomCardCenti(ownerId, 0);
        HttpResponse<String> insufficient =
                post(
                        "/api/v1/rooms",
                        defaultWulongRequest(),
                        accessToken,
                        "Idempotency-Key",
                        "room-" + UUID.randomUUID());
        assertThat(insufficient.statusCode()).isEqualTo(409);
        assertThat(json(insufficient.body()).path("code").asText())
                .isEqualTo("ROOM_INSUFFICIENT_BALANCE");

        String forgedBody = defaultWulongRequest().replace("\"ShowCardCount\",", "\"Forged='1';\",");
        setRoomCardCenti(ownerId, 100);
        HttpResponse<String> forged =
                post(
                        "/api/v1/rooms",
                        forgedBody,
                        accessToken,
                        "Idempotency-Key",
                        "room-" + UUID.randomUUID());
        assertThat(forged.statusCode()).isEqualTo(400);
        assertThat(json(forged.body()).path("code").asText())
                .isEqualTo("ROOM_RULE_INVALID");
    }

    @Test
    void createsFromOriginalSuichangCategoryWithoutAPlayCountGroup() throws Exception {
        Login owner = loginUser();
        setRoomCardCenti(owner.userId(), 200);
        String request =
                """
                {
                  "lobbyId": 900038,
                  "gameId": 30300,
                  "categoryIndex": 1,
                  "selectedNodeNames": ["gameType='1';", "playerCount_4"]
                }
                """;

        HttpResponse<String> response =
                post(
                        "/api/v1/rooms",
                        request,
                        owner.accessToken(),
                        "Idempotency-Key",
                        "room-" + UUID.randomUUID());

        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode room = json(response.body());
        assertThat(room.path("playerCount").asInt()).isEqualTo(4);
        assertThat(room.path("playCount").asInt()).isEqualTo(8);
        assertThat(room.path("payType").asText()).isEqualTo("ALL");
        assertThat(room.path("roomFeeCenti").asInt()).isEqualTo(200);
        assertThat(room.path("gameRule").asText())
                .isEqualTo(
                        "gameType='1';UserRule='AutoReady=false;';"
                                + "IsSysTrust='0';RoomFee='2';");

        Login mismatchOwner = loginUser();
        setRoomCardCenti(mismatchOwner.userId(), 200);
        HttpResponse<String> mismatch =
                post(
                        "/api/v1/rooms",
                        request.replace("gameType='1';", "gameType='0';"),
                        mismatchOwner.accessToken(),
                        "Idempotency-Key",
                        "room-" + UUID.randomUUID());
        assertThat(mismatch.statusCode()).isEqualTo(400);
        assertThat(json(mismatch.body()).path("code").asText())
                .isEqualTo("ROOM_RULE_INVALID");
    }
}
