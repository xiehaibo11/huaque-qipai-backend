package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
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
class BackendQaBotAutoRoundFlowIT extends RoomFlowTestSupport {
    @Test
    void qaAutoRoundFillsBotsAndWaitsForTheHumanMultipleChoiceBeforeDealing() throws Exception {
        Login owner = loginUser();
        setRoomCardCenti(owner.userId(), 400);
        String roomNumber = createTaizhouRoom(owner.accessToken());
        JsonNode opened =
                json(
                        post(
                                        "/api/v1/game-sessions/" + roomNumber,
                                        "{}",
                                        owner.accessToken(),
                                        null,
                                        null)
                                .body());
        assertThat(opened.path("revision").asLong()).isZero();
        HttpResponse<String> command =
                post(
                        "/api/v1/game-sessions/" + roomNumber + "/commands",
                        "{\"type\":\"QA_AUTO_ROUND\",\"expectedRevision\":0}",
                        owner.accessToken(),
                        "Idempotency-Key",
                        "qa-auto-" + UUID.randomUUID());

        assertThat(command.statusCode()).isEqualTo(200);
        JsonNode accepted = json(command.body());
        assertThat(accepted.path("revision").asLong()).isEqualTo(1L);
        assertThat(accepted.path("eventType").asText()).isEqualTo("BOT_SEATS_FILLED");
        assertThat(qaBotIdentityCount()).isGreaterThanOrEqualTo(10L);

        JsonNode events =
                json(
                        get(
                                        "/api/v1/game-sessions/" + roomNumber + "/events",
                                        owner.accessToken())
                                .body());
        assertThat(events).hasSizeGreaterThanOrEqualTo(3);
        assertThat(eventTypes(events))
                .containsSubsequence(
                        "BOT_SEATS_FILLED",
                        "WALL_SHUFFLED",
                        "MULTIPLE_CHOICE_STARTED")
                .doesNotContain("DEALT", "DRAWN", "ACTION_OFFERED");
        JsonNode multipleStarted = eventByType(events, "MULTIPLE_CHOICE_STARTED");
        assertThat(multipleStarted.path("payload").path("qaMode").asBoolean()).isTrue();
        assertThat(multipleStarted.path("payload").path("multipleChoice").path("goldMode").asBoolean())
                .isFalse();
        assertThat(multipleStarted.path("payload").path("multipleChoice").path("allowedChoices"))
                .hasSize(3);

        JsonNode snapshot =
                json(get("/api/v1/game-sessions/" + roomNumber, owner.accessToken()).body());
        assertThat(snapshot.path("phase").asText()).isEqualTo("DEALING");
        assertThat(snapshot.path("roundNumber").asInt()).isEqualTo(1);
        assertThat(snapshot.path("revision").asLong()).isEqualTo(1L);
        assertThat(snapshot.path("seats")).hasSize(4);
        assertThat(snapshot.path("seats").get(1).path("displayName").asText())
                .doesNotContain("测试")
                .doesNotContain("假人")
                .doesNotContain("机用户");
        assertThat(snapshot.path("multipleChoice").path("choiceActive").asBoolean()).isTrue();
        assertThat(snapshot.path("multipleChoice").path("goldMode").asBoolean()).isFalse();
        assertThat(snapshot.path("visibleRound").path("hands")).hasSize(4);
        assertThat(snapshot.path("visibleRound").path("hands").get(0).path("concealedTiles")).isEmpty();
        assertThat(snapshot.has("settlement")).isFalse();
        assertThat(snapshot.path("activeSeat").asInt()).isEqualTo(1);

        driveQaTaizhouRoundToCompletion(owner.accessToken(), roomNumber);

        JsonNode finishedEvents =
                json(get("/api/v1/game-sessions/" + roomNumber + "/events", owner.accessToken()).body());
        assertThat(eventTypes(finishedEvents))
                .containsSubsequence("SCORES_SETTLED", "ROUND_RESULT_READY");
        JsonNode finished =
                json(get("/api/v1/game-sessions/" + roomNumber, owner.accessToken()).body());
        assertThat(finished.path("phase").asText()).isEqualTo("ROUND_RESULT");
        assertThat(finished.path("settlement").path("seats")).hasSize(4);
        assertThat(finished.path("settlement").path("result").asText())
                .isIn("ZIMO", "DIANPAO", "DRAWN");
    }

    @Test
    void normalStartRoundRequiresAFullReadyRoom() throws Exception {
        Login owner = loginUser();
        setRoomCardCenti(owner.userId(), 400);
        String roomNumber = createTaizhouRoom(owner.accessToken());
        post("/api/v1/game-sessions/" + roomNumber, "{}", owner.accessToken(), null, null);

        HttpResponse<String> command =
                post(
                        "/api/v1/game-sessions/" + roomNumber + "/commands",
                        "{\"type\":\"START_ROUND\",\"expectedRevision\":0}",
                        owner.accessToken(),
                        "Idempotency-Key",
                        "start-" + UUID.randomUUID());

        assertThat(command.statusCode()).isEqualTo(409);
        assertThat(json(command.body()).path("code").asText())
                .isEqualTo("ROOM_NOT_FULL");
    }

    @Test
    void normalStartRoundStartsServerAuthorityForAFullReadyRealRoom() throws Exception {
        Login owner = loginUser();
        setRoomCardCenti(owner.userId(), 400);
        String roomNumber = createTaizhouRoom(owner.accessToken());
        List<Login> participants = List.of(loginUser(), loginUser(), loginUser());
        for (Login participant : participants) {
            HttpResponse<String> joined =
                    post(
                            "/api/v1/rooms/" + roomNumber + "/join",
                            "{}",
                            participant.accessToken(),
                            null,
                            null);
            assertThat(joined.statusCode()).isEqualTo(200);
        }
        HttpResponse<String> charged =
                post(
                        "/api/v1/rooms/" + roomNumber + "/first-round",
                        "{}",
                        owner.accessToken(),
                        null,
                        null);
        assertThat(charged.statusCode()).isEqualTo(200);
        assertThat(json(charged.body()).path("status").asText()).isEqualTo("CHARGED");

        JsonNode opened =
                json(post("/api/v1/game-sessions/" + roomNumber, "{}", owner.accessToken(), null, null).body());
        assertThat(opened.path("revision").asLong()).isZero();
        assertThat(opened.path("autoReady").asBoolean()).isTrue();
        assertThat(opened.path("seats")).hasSize(4);
        HttpResponse<String> ready =
                post(
                        "/api/v1/game-sessions/" + roomNumber + "/commands",
                        "{\"type\":\"READY\",\"expectedRevision\":0}",
                        owner.accessToken(),
                        "Idempotency-Key",
                        "ready-" + UUID.randomUUID());
        assertThat(ready.statusCode()).isEqualTo(200);
        assertThat(json(ready.body()).path("revision").asLong()).isEqualTo(1L);
        JsonNode readySnapshot =
                json(get("/api/v1/game-sessions/" + roomNumber, owner.accessToken()).body());
        assertThat(allSeatsReady(readySnapshot)).isTrue();

        HttpResponse<String> started =
                post(
                        "/api/v1/game-sessions/" + roomNumber + "/commands",
                        "{\"type\":\"START_ROUND\",\"expectedRevision\":1}",
                        owner.accessToken(),
                        "Idempotency-Key",
                        "start-" + UUID.randomUUID());
        assertThat(started.statusCode()).isEqualTo(200);
        JsonNode accepted = json(started.body());
        assertThat(accepted.path("revision").asLong()).isEqualTo(2L);
        assertThat(accepted.path("eventType").asText()).isEqualTo("WALL_SHUFFLED");

        JsonNode snapshot =
                json(get("/api/v1/game-sessions/" + roomNumber, owner.accessToken()).body());
        assertThat(snapshot.path("phase").asText()).isEqualTo("DEALING");
        assertThat(snapshot.path("revision").asLong()).isEqualTo(2L);
        assertThat(snapshot.path("multipleChoice").path("choiceActive").asBoolean()).isTrue();
        assertThat(snapshot.path("visibleRound").path("hands")).hasSize(4);
        assertThat(snapshot.path("visibleRound").path("hands").get(0).path("concealedTiles")).isEmpty();

        JsonNode events = json(get("/api/v1/game-sessions/" + roomNumber + "/events", owner.accessToken()).body());
        assertThat(eventTypes(events))
                .contains("WALL_SHUFFLED", "MULTIPLE_CHOICE_STARTED")
                .doesNotContain("DEALT", "ACTION_OFFERED");
        JsonNode wall = eventByType(events, "WALL_SHUFFLED");
        assertThat(wall.path("payload").path("engineMode").asText()).isEqualTo("SERVER_AUTHORITY");
        assertThat(wall.path("payload").path("serverAuthority").asBoolean()).isTrue();

        String storedState =
                jdbcTemplate.queryForObject(
                        """
                        select s.state from game_sessions s
                        join game_rooms r on r.id = s.room_id
                        where r.room_number = ?
                        """,
                        String.class,
                        roomNumber);
        JsonNode state = json(storedState);
        assertThat(state.path("engineMode").asText()).isEqualTo("SERVER_AUTHORITY");
        assertThat(state.path("serverAuthority").asBoolean()).isTrue();
        assertThat(state.has("qaDisclosure")).isFalse();
    }

    private String createTaizhouRoom(String accessToken) throws Exception {
        HttpResponse<String> created =
                post(
                        "/api/v1/rooms",
                        defaultTaizhouMahjongRequest(),
                        accessToken,
                        "Idempotency-Key",
                        "room-" + UUID.randomUUID());
        assertThat(created.statusCode()).isEqualTo(201);
        return json(created.body()).path("roomNumber").asText();
    }

    private long qaBotIdentityCount() {
        return jdbcTemplate.queryForObject(
                """
                select count(*) from user_identities
                where provider = 'QA_BOT' and provider_subject like 'taizhou-mahjong-bot-%'
                """,
                Long.class);
    }


    private static JsonNode eventByType(JsonNode events, String type) {
        for (JsonNode event : events) {
            if (event.path("type").asText().equals(type)) {
                return event;
            }
        }
        throw new AssertionError("missing event " + type);
    }

    private static java.util.List<String> eventTypes(JsonNode events) {
        java.util.List<String> types = new java.util.ArrayList<>();
        events.forEach(event -> types.add(event.path("type").asText()));
        return types;
    }

    private static boolean allSeatsReady(JsonNode snapshot) {
        for (JsonNode seat : snapshot.path("seats")) {
            if (!seat.path("ready").asBoolean()) {
                return false;
            }
        }
        return true;
    }

    private static String defaultTaizhouMahjongRequest() {
        return """
                {
                  "lobbyId": 900023,
                  "gameId": 30109,
                  "categoryIndex": 1,
                  "selectedNodeNames": [
                    "winLostType='1';",
                    "playerCount_4",
                    "maxQuanShu='2';",
                    "liaoDaZiBaoPai",
                    "buSiBao",
                    "FengDing='0';",
                    "PayType='0';",
                    "autoReady",
                    "forceGPS",
                    "IsSysTrust='0';"
                  ]
                }
                """;
    }
}
