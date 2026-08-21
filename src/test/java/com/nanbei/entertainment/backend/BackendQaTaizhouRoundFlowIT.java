package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

/**
 * 金币 join QA 链路 → 真人 DISCARD/CHOW/PUNG/HU/PASS/MULTIPLE_CHOICE 全流程。
 * 引擎规则为南北自建 QA 规则（state 含 qaDisclosure）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Import(BackendFlowTestcontainersConfiguration.class)
class BackendQaTaizhouRoundFlowIT extends RoomFlowTestSupport {
    private static final String JOIN_SENIOR =
            """
            {"lobbyId":900023,"roomNameFlag":3}
            """;

    @Test
    void goldQaJoinThenTheHumanPlaysAFullRoundToTheResult() throws Exception {
        Login login = loginUser();
        setCoins(login.userId(), 1_000_000L);
        JsonNode joined =
                json(
                        post(
                                        "/api/v1/gold-rooms/games/30400/join",
                                        JOIN_SENIOR,
                                        login.accessToken(),
                                        "Idempotency-Key",
                                        "gold-join-" + UUID.randomUUID())
                                .body());
        assertThat(joined.path("code").asText()).isEqualTo("GOLD_QA_AUTO_ROUND_READY");
        String roomNumber = joined.path("roomNumber").asText();

        JsonNode snapshot =
                json(get("/api/v1/game-sessions/" + roomNumber, login.accessToken()).body());
        assertThat(snapshot.path("phase").asText()).isEqualTo("DEALING");
        assertThat(snapshot.path("mySeat").asInt()).isEqualTo(1);
        assertThat(snapshot.path("multipleChoice").path("choiceActive").asBoolean()).isTrue();

        HttpResponse<String> choice =
                post(
                        "/api/v1/game-sessions/" + roomNumber + "/commands",
                        "{\"type\":\"MULTIPLE_CHOICE\",\"expectedRevision\":"
                                + snapshot.path("revision").asLong()
                                + ",\"payload\":{\"choice\":\"ADD\"}}",
                        login.accessToken(),
                        "Idempotency-Key",
                        "qa-choice-" + UUID.randomUUID());
        assertThat(choice.statusCode()).isEqualTo(200);
        assertThat(json(choice.body()).path("eventType").asText())
                .isEqualTo("MULTIPLE_CHOICE_CHANGED");

        JsonNode playing =
                json(get("/api/v1/game-sessions/" + roomNumber, login.accessToken()).body());
        assertThat(playing.path("phase").asText()).isEqualTo("PLAYING");
        String firstToken = playing.path("playPermission").path("actionToken").asText();
        assertThat(firstToken).isNotBlank();
        int firstTile = maxConcealedTile(playing, 1);

        HttpResponse<String> discarded =
                post(
                        "/api/v1/game-sessions/" + roomNumber + "/commands",
                        "{\"type\":\"DISCARD\",\"expectedRevision\":"
                                + playing.path("revision").asLong()
                                + ",\"payload\":{\"tileValue\":"
                                + firstTile
                                + ",\"actionToken\":\""
                                + firstToken
                                + "\"}}",
                        login.accessToken(),
                        "Idempotency-Key",
                        "qa-discard-" + UUID.randomUUID());
        assertThat(discarded.statusCode()).isEqualTo(200);
        assertThat(json(discarded.body()).path("eventType").asText()).isEqualTo("DISCARDED");

        // actionToken 一次性：同一 token 重放（换新的幂等键）必须被 409 拒绝。
        JsonNode afterDiscard =
                json(get("/api/v1/game-sessions/" + roomNumber, login.accessToken()).body());
        HttpResponse<String> replayedToken =
                post(
                        "/api/v1/game-sessions/" + roomNumber + "/commands",
                        "{\"type\":\"DISCARD\",\"expectedRevision\":"
                                + afterDiscard.path("revision").asLong()
                                + ",\"payload\":{\"tileValue\":"
                                + firstTile
                                + ",\"actionToken\":\""
                                + firstToken
                                + "\"}}",
                        login.accessToken(),
                        "Idempotency-Key",
                        "qa-discard-replay-" + UUID.randomUUID());
        assertThat(replayedToken.statusCode()).isEqualTo(409);
        assertThat(json(replayedToken.body()).path("code").asText())
                .isEqualTo("GAME_ACTION_NOT_ALLOWED");

        driveQaTaizhouRoundToCompletion(login.accessToken(), roomNumber);

        JsonNode finishedEvents =
                json(get("/api/v1/game-sessions/" + roomNumber + "/events", login.accessToken()).body());
        assertThat(eventTypes(finishedEvents))
                .contains("DISCARDED", "TURN_ADVANCED", "SCORES_SETTLED", "ROUND_RESULT_READY");
        JsonNode finished =
                json(get("/api/v1/game-sessions/" + roomNumber, login.accessToken()).body());
        assertThat(finished.path("phase").asText()).isEqualTo("ROUND_RESULT");
        assertThat(finished.path("settlement").path("seats")).hasSize(4);
        assertThat(finished.path("settlement").path("result").asText())
                .isIn("ZIMO", "DIANPAO", "DRAWN");
        assertThat(finished.path("settlement").path("seats").get(0).path("endPlayerState").asText())
                .startsWith("EPS_");
        // 局终后再发命令被明确拒绝。
        HttpResponse<String> afterEnd =
                post(
                        "/api/v1/game-sessions/" + roomNumber + "/commands",
                        "{\"type\":\"PASS\",\"expectedRevision\":"
                                + finished.path("revision").asLong()
                                + ",\"payload\":{\"actionToken\":\""
                                + firstToken
                                + "\"}}",
                        login.accessToken(),
                        "Idempotency-Key",
                        "qa-after-end-" + UUID.randomUUID());
        assertThat(afterEnd.statusCode()).isEqualTo(409);
    }

    @Test
    void roundCommandsBeforeStartRoundAreRejectedAsOutOfPhase() throws Exception {
        Login owner = loginUser();
        setRoomCardCenti(owner.userId(), 400);
        String roomNumber = createPlainTaizhouRoom(owner.accessToken());
        post("/api/v1/game-sessions/" + roomNumber, "{}", owner.accessToken(), null, null);

        HttpResponse<String> command =
                post(
                        "/api/v1/game-sessions/" + roomNumber + "/commands",
                        "{\"type\":\"DISCARD\",\"expectedRevision\":0,"
                                + "\"payload\":{\"tileValue\":17,\"actionToken\":\"t\"}}",
                        owner.accessToken(),
                        "Idempotency-Key",
                        "qa-gate-" + UUID.randomUUID());

        assertThat(command.statusCode()).isEqualTo(409);
        assertThat(json(command.body()).path("code").asText())
                .isEqualTo("GAME_ACTION_NOT_ALLOWED");
    }

    private static int maxConcealedTile(JsonNode snapshot, int mySeat) {
        int tile = -1;
        for (JsonNode hand : snapshot.path("visibleRound").path("hands")) {
            if (hand.path("seatNumber").asInt() == mySeat) {
                for (JsonNode concealed : hand.path("concealedTiles")) {
                    tile = Math.max(tile, concealed.asInt());
                }
            }
        }
        assertThat(tile).isPositive();
        return tile;
    }

    private String createPlainTaizhouRoom(String accessToken) throws Exception {
        HttpResponse<String> created =
                post(
                        "/api/v1/rooms",
                        """
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
                        """,
                        accessToken,
                        "Idempotency-Key",
                        "room-" + UUID.randomUUID());
        assertThat(created.statusCode()).isEqualTo(201);
        return json(created.body()).path("roomNumber").asText();
    }

    private static java.util.List<String> eventTypes(JsonNode events) {
        java.util.List<String> types = new java.util.ArrayList<>();
        events.forEach(event -> types.add(event.path("type").asText()));
        return types;
    }
}
