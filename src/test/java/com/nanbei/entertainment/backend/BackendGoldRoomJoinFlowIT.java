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
class BackendGoldRoomJoinFlowIT extends RoomFlowTestSupport {
    private static final String JOIN_SENIOR =
            """
            {"lobbyId":900023,"roomNameFlag":3}
            """;

    @Test
    void rejectsAnonymousJoinRequests() throws Exception {
        HttpResponse<String> response =
                post(
                        "/api/v1/gold-rooms/games/30400/join",
                        JOIN_SENIOR,
                        null,
                        "Idempotency-Key",
                        "gold-join-" + UUID.randomUUID());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void localQaJoinAddsTestBotsAndStartsTheMatchedTaizhouRound()
            throws Exception {
        Login login = loginUser();
        setCoins(login.userId(), 1_000_000L);
        long sessionsBefore = gameSessionCount();
        HttpResponse<String> response =
                post(
                        "/api/v1/gold-rooms/games/30400/join",
                        JOIN_SENIOR,
                        login.accessToken(),
                        "Idempotency-Key",
                        "gold-join-" + UUID.randomUUID());

        assertThat(response.statusCode()).isEqualTo(202);
        JsonNode body = json(response.body());
        assertThat(body.path("code").asText()).isEqualTo("GOLD_QA_AUTO_ROUND_READY");
        assertThat(body.path("status").asText()).isEqualTo("READY");
        assertThat(body.path("roomMode").asText()).isEqualTo("QA_AUTO_MATCH");
        assertThat(body.path("lobbyId").asLong()).isEqualTo(900023L);
        assertThat(body.path("gameId").asLong()).isEqualTo(30400L);
        assertThat(body.path("boxGameId").asLong()).isEqualTo(30109L);
        assertThat(body.path("roomNameFlag").asInt()).isEqualTo(3);
        assertThat(body.path("sessionId").asInt()).isEqualTo(3);
        assertThat(body.path("chairCount").asInt()).isEqualTo(4);
        assertThat(body.path("baseScore").asLong()).isEqualTo(1000L);
        assertThat(body.path("dynamicCost").asBoolean()).isTrue();
        assertThat(body.path("minRich").asLong()).isEqualTo(50_000L);
        assertThat(body.path("maxRich").asLong()).isEqualTo(-1L);
        assertThat(body.path("message").asText()).isEqualTo("牌友已加入，自动牌局已开始");
        assertThat(body.path("matchingTicketId").asText()).isNotBlank();
        assertThat(body.path("roomNumber").asText()).matches("\\d{6}");
        assertThat(body.path("autoGameplay").asBoolean()).isTrue();
        assertThat(body.path("replay").asBoolean()).isFalse();
        assertThat(gameSessionCount()).isEqualTo(sessionsBefore + 1L);
        assertThat(qaBotIdentityCount()).isGreaterThanOrEqualTo(300L);
        assertThat(recentQaBotActiveCount()).isGreaterThanOrEqualTo(300L);

        HttpResponse<String> snapshotResponse =
                get("/api/v1/game-sessions/" + body.path("roomNumber").asText(), login.accessToken());
        assertThat(snapshotResponse.statusCode())
                .as(snapshotResponse.body())
                .isEqualTo(200);
        JsonNode snapshot = json(snapshotResponse.body());
        assertThat(snapshot.path("phase").asText()).isEqualTo("DEALING");
        assertThat(snapshot.path("gameId").asLong()).isEqualTo(30109L);
        assertThat(snapshot.path("roomMode").asInt()).isEqualTo(50);
        assertThat(snapshot.path("roomVenue").asText()).isEqualTo("GOLD");
        assertThat(snapshot.path("seats")).hasSize(4);
        assertThat(snapshot.path("seats").get(1).path("displayName").asText())
                .doesNotContainIgnoringCase("AI")
                .doesNotContain("机器人")
                .doesNotContain("测试")
                .doesNotContain("假人")
                .doesNotContain("机用户");
        assertThat(snapshot.path("seats").get(0).path("score").asLong()).isEqualTo(1_000_000L);
        assertThat(snapshot.path("seats").get(1).path("score").asLong()).isEqualTo(50_000L);
        assertThat(snapshot.path("seats").get(2).path("score").asLong()).isEqualTo(50_000L);
        assertThat(snapshot.path("seats").get(3).path("score").asLong()).isEqualTo(50_000L);
        assertThat(snapshot.path("multipleChoice").path("choiceActive").asBoolean()).isTrue();
        assertThat(snapshot.path("multipleChoice").path("goldMode").asBoolean()).isTrue();
        assertThat(snapshot.path("multipleChoice").path("seatChoices").get(1).path("choice").asText())
                .isEqualTo("DEFAULT");
        assertThat(snapshot.path("multipleChoice").path("seatChoices").get(2).path("choice").asText())
                .isEqualTo("SUPER");
        assertThat(snapshot.path("multipleChoice").path("seatChoices").get(3).path("choice").asText())
                .isEqualTo("PASS");
        assertThat(snapshot.path("visibleRound").path("hands")).hasSize(4);
        assertThat(snapshot.path("visibleRound").path("hands").get(0).path("concealedTiles")).isEmpty();
        assertThat(snapshot.has("settlement")).isFalse();
    }

    @Test
    void localQaJoinReopensTheExistingLiveTestRoomForTheSameUser()
            throws Exception {
        Login login = loginUser();
        setCoins(login.userId(), 1_000_000L);
        long sessionsBefore = gameSessionCount();
        JsonNode first =
                json(
                        post(
                                        "/api/v1/gold-rooms/games/30400/join",
                                        JOIN_SENIOR,
                                        login.accessToken(),
                                        "Idempotency-Key",
                                        "gold-join-" + UUID.randomUUID())
                                .body());

        HttpResponse<String> secondResponse =
                post(
                        "/api/v1/gold-rooms/games/30400/join",
                        JOIN_SENIOR,
                        login.accessToken(),
                        "Idempotency-Key",
                        "gold-join-" + UUID.randomUUID());

        assertThat(secondResponse.statusCode()).isEqualTo(202);
        JsonNode second = json(secondResponse.body());
        assertThat(second.path("code").asText()).isEqualTo("GOLD_QA_AUTO_ROUND_READY");
        assertThat(second.path("roomMode").asText()).isEqualTo("QA_AUTO_MATCH");
        assertThat(second.path("roomNumber").asText()).isEqualTo(first.path("roomNumber").asText());
        assertThat(second.path("autoGameplay").asBoolean()).isTrue();
        assertThat(gameSessionCount()).isEqualTo(sessionsBefore + 1L);

        HttpResponse<String> snapshotResponse =
                get("/api/v1/game-sessions/" + second.path("roomNumber").asText(), login.accessToken());
        assertThat(snapshotResponse.statusCode())
                .as(snapshotResponse.body())
                .isEqualTo(200);
        JsonNode snapshot = json(snapshotResponse.body());
        assertThat(snapshot.path("phase").asText()).isEqualTo("DEALING");
        assertThat(snapshot.path("seats").get(1).path("displayName").asText())
                .doesNotContain("测试")
                .doesNotContain("假人")
                .doesNotContain("机用户");
        assertThat(snapshot.path("multipleChoice").path("choiceActive").asBoolean()).isTrue();
    }

    @Test
    void localQaJoinCreatesATestTableWhenTheUserHasAnOpenCreateRoom()
            throws Exception {
        Login login = loginUser();
        setRoomCardCenti(login.userId(), 400);
        setCoins(login.userId(), 1_000_000L);
        String openRoomNumber = createPlainTaizhouRoom(login.accessToken());

        HttpResponse<String> response =
                post(
                        "/api/v1/gold-rooms/games/30400/join",
                        JOIN_SENIOR,
                        login.accessToken(),
                        "Idempotency-Key",
                        "gold-join-" + UUID.randomUUID());

        assertThat(response.statusCode()).isEqualTo(202);
        JsonNode body = json(response.body());
        String qaRoomNumber = body.path("roomNumber").asText();
        assertThat(body.path("code").asText()).isEqualTo("GOLD_QA_AUTO_ROUND_READY");
        assertThat(qaRoomNumber).matches("\\d{6}");
        assertThat(qaRoomNumber).isNotEqualTo(openRoomNumber);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select status from game_rooms where room_number = ?",
                                String.class,
                                openRoomNumber))
                .isEqualTo("OPEN");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select owner_user_id from game_rooms where room_number = ?",
                                UUID.class,
                                qaRoomNumber))
                .isNotEqualTo(login.userId());
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                select count(*)
                                from room_participants participant
                                join game_rooms room on room.id = participant.room_id
                                where room.room_number = ? and participant.user_id = ?
                                """,
                                Long.class,
                                qaRoomNumber,
                                login.userId()))
                .isEqualTo(1L);
    }

    @Test
    void localQaJoinReplacesLegacyLiveTestRoomWithoutQaRound()
            throws Exception {
        Login login = loginUser();
        setCoins(login.userId(), 1_000_000L);
        JsonNode first =
                json(
                        post(
                                        "/api/v1/gold-rooms/games/30400/join",
                                        JOIN_SENIOR,
                                        login.accessToken(),
                                        "Idempotency-Key",
                                        "gold-join-" + UUID.randomUUID())
                                .body());
        String oldRoomNumber = first.path("roomNumber").asText();
        jdbcTemplate.update(
                """
                update game_sessions
                set state = '{"qaMode":"AUTO_ROUND"}'::jsonb
                where room_id = (select id from game_rooms where room_number = ?)
                """,
                oldRoomNumber);

        HttpResponse<String> secondResponse =
                post(
                        "/api/v1/gold-rooms/games/30400/join",
                        JOIN_SENIOR,
                        login.accessToken(),
                        "Idempotency-Key",
                        "gold-join-" + UUID.randomUUID());

        assertThat(secondResponse.statusCode()).isEqualTo(202);
        JsonNode second = json(secondResponse.body());
        String newRoomNumber = second.path("roomNumber").asText();
        assertThat(newRoomNumber).matches("\\d{6}");
        assertThat(newRoomNumber).isNotEqualTo(oldRoomNumber);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select status from game_rooms where room_number = ?",
                                String.class,
                                oldRoomNumber))
                .isEqualTo("DISSOLVED");
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                select jsonb_exists(state, 'qaRound')
                                from game_sessions
                                where room_id = (select id from game_rooms where room_number = ?)
                                """,
                                Boolean.class,
                                newRoomNumber))
                .isTrue();
    }

    @Test
    void localQaJoinReplacesLiveTestRoomWithIncompatiblePlayPermissionIndexes()
            throws Exception {
        Login login = loginUser();
        setCoins(login.userId(), 1_000_000L);
        JsonNode first =
                json(
                        post(
                                        "/api/v1/gold-rooms/games/30400/join",
                                        JOIN_SENIOR,
                                        login.accessToken(),
                                        "Idempotency-Key",
                                        "gold-join-" + UUID.randomUUID())
                                .body());
        String oldRoomNumber = first.path("roomNumber").asText();
        jdbcTemplate.update(
                """
                update game_sessions
                set state = '{
                  "qaRound": {},
                  "visibleRoundsBySeat": {
                    "1": {
                      "chairCount": 4,
                      "mySeat": 1,
                      "hands": [
                        {"seatNumber": 1, "concealedTiles": [17,18], "meldCount": 0},
                        {"seatNumber": 2, "concealedTiles": [114], "meldCount": 0},
                        {"seatNumber": 3, "concealedTiles": [114], "meldCount": 0},
                        {"seatNumber": 4, "concealedTiles": [114], "meldCount": 0}
                      ],
                      "jokerTiles": [],
                      "insteadTiles": []
                    }
                  },
                  "playPermissionsBySeat": {
                    "1": {
                      "actionToken": "stale-token",
                      "mode": "SINGLE_CLICK",
                      "playableOriginalIndexes": [0,1],
                      "tingOriginalIndexes": [],
                      "actionMaskOriginalIndexes": [],
                      "preBaoOriginalIndexes": []
                    }
                  }
                }'::jsonb
                where room_id = (select id from game_rooms where room_number = ?)
                """,
                oldRoomNumber);

        HttpResponse<String> secondResponse =
                post(
                        "/api/v1/gold-rooms/games/30400/join",
                        JOIN_SENIOR,
                        login.accessToken(),
                        "Idempotency-Key",
                        "gold-join-" + UUID.randomUUID());

        assertThat(secondResponse.statusCode()).isEqualTo(202);
        JsonNode second = json(secondResponse.body());
        String newRoomNumber = second.path("roomNumber").asText();
        assertThat(newRoomNumber).matches("\\d{6}");
        assertThat(newRoomNumber).isNotEqualTo(oldRoomNumber);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select status from game_rooms where room_number = ?",
                                String.class,
                                oldRoomNumber))
                .isEqualTo("DISSOLVED");
    }

    @Test
    void rejectsTooRichUsersFromLowerLevelsWithTheOriginalHighLimitCode()
            throws Exception {
        Login login = loginUser();
        setCoins(login.userId(), 1_000_000L);

        HttpResponse<String> response =
                post(
                        "/api/v1/gold-rooms/games/30400/join",
                        "{\"lobbyId\":900023,\"roomNameFlag\":1}",
                        login.accessToken(),
                        "Idempotency-Key",
                        "gold-join-" + UUID.randomUUID());

        assertThat(response.statusCode()).isEqualTo(409);
        JsonNode body = json(response.body());
        assertThat(body.path("code").asText()).isEqualTo("GOLD_HIGH_LIMIT");
        assertThat(body.path("detail").asText())
                .isEqualTo("金币满载，请前往更高级房间，体验更丰富的游戏乐趣!");
    }

    @Test
    void rejectsLowBalanceWithTheOriginalLowLimitMessage() throws Exception {
        Login login = loginUser();
        setCoins(login.userId(), 0L);

        HttpResponse<String> response =
                post(
                        "/api/v1/gold-rooms/games/30400/join",
                        "{\"lobbyId\":900023,\"roomNameFlag\":1}",
                        login.accessToken(),
                        "Idempotency-Key",
                        "gold-join-" + UUID.randomUUID());

        assertThat(response.statusCode()).isEqualTo(409);
        JsonNode body = json(response.body());
        assertThat(body.path("code").asText()).isEqualTo("GOLD_LOW_LIMIT");
        assertThat(body.path("detail").asText()).isEqualTo("金币不足！补充金币，再战四方！");
    }

    @Test
    void replaysIdenticalJoinRequestsAndRejectsIdempotencyConflicts()
            throws Exception {
        Login login = loginUser();
        setCoins(login.userId(), 1_000_000L);
        String idempotencyKey = "gold-join-" + UUID.randomUUID();

        HttpResponse<String> created =
                post(
                        "/api/v1/gold-rooms/games/30400/join",
                        JOIN_SENIOR,
                        login.accessToken(),
                        "Idempotency-Key",
                        idempotencyKey);
        HttpResponse<String> replayed =
                post(
                        "/api/v1/gold-rooms/games/30400/join",
                        JOIN_SENIOR,
                        login.accessToken(),
                        "Idempotency-Key",
                        idempotencyKey);
        HttpResponse<String> conflict =
                post(
                        "/api/v1/gold-rooms/games/30400/join",
                        "{\"lobbyId\":900023,\"roomNameFlag\":2}",
                        login.accessToken(),
                        "Idempotency-Key",
                        idempotencyKey);

        assertThat(created.statusCode()).isEqualTo(202);
        assertThat(replayed.statusCode()).isEqualTo(200);
        assertThat(json(replayed.body()).path("replay").asBoolean()).isTrue();
        assertThat(json(replayed.body()).path("matchingTicketId").asText())
                .isEqualTo(json(created.body()).path("matchingTicketId").asText());
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(json(conflict.body()).path("code").asText())
                .isEqualTo("GOLD_ROOM_IDEMPOTENCY_CONFLICT");
    }
    private long qaBotIdentityCount() {
        return jdbcTemplate.queryForObject(
                """
                select count(*) from user_identities
                where provider = 'QA_BOT' and provider_subject like 'taizhou-mahjong-bot-%'
                """,
                Long.class);
    }

    private long recentQaBotActiveCount() {
        return jdbcTemplate.queryForObject(
                """
                select count(*)
                from user_identities identity
                join app_users users on users.id = identity.user_id
                where identity.provider = 'QA_BOT'
                  and identity.provider_subject like 'taizhou-mahjong-bot-%'
                  and users.last_active_at >= current_timestamp - interval '5 minutes'
                """,
                Long.class);
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

}
