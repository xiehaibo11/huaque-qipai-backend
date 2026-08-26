package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanbei.entertainment.backend.gameplay.application.GoldRoomMatchService;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "nanbei.gameplay.qa.enabled=false")
@ActiveProfiles("local")
@Import(BackendFlowTestcontainersConfiguration.class)
class BackendGoldRoomLeaveFlowIT extends RoomFlowTestSupport {
    @Autowired GoldRoomMatchService matchService;

    private static final String JOIN_NOVICE =
            """
            {"lobbyId":900023,"roomNameFlag":1}
            """;
    private static final String JOIN_ADVANCED =
            """
            {"lobbyId":900023,"roomNameFlag":2}
            """;
    private static final String LEAVE_NOVICE =
            """
            {"lobbyId":900023,"roomNameFlag":1}
            """;

    // JUnit 方法顺序不定，而 findMatchableGoldRooms 会全库复用同 matchKey 的 OPEN 未满房：
    // 上一轮残留的匹配房会把后续测试的 join 拼进旧房，必须按外键顺序清空房间相关表。
    @AfterEach
    void cleanGoldRooms() {
        jdbcTemplate.update("delete from game_events");
        jdbcTemplate.update("delete from game_session_seats");
        jdbcTemplate.update("delete from game_sessions");
        jdbcTemplate.update("delete from room_participants");
        jdbcTemplate.update("delete from game_rooms");
    }

    @Test
    void leaveCancelsThePendingMatchAndUnlocksOtherLevels() throws Exception {
        Login player = loginUser();
        // 40000 金币同时落在新手场与进阶场区间，两个场次都能通过门槛校验。
        setCoins(player.userId(), 40_000L);
        HttpResponse<String> join =
                post(
                        "/api/v1/gold-rooms/games/30400/join",
                        JOIN_NOVICE,
                        player.accessToken(),
                        "Idempotency-Key",
                        "gold-leave-join-" + UUID.randomUUID());
        assertThat(join.statusCode()).isEqualTo(202);
        JsonNode joinBody = json(join.body());
        assertThat(joinBody.path("status").asText()).isEqualTo("MATCHING");
        String roomNumber = joinBody.path("roomNumber").asText();

        // 原版 MatchServer GOLD_QUEUING：玩家仍在队列中时加入其他场次被拒。
        HttpResponse<String> crossLevelJoin =
                post(
                        "/api/v1/gold-rooms/games/30400/join",
                        JOIN_ADVANCED,
                        player.accessToken(),
                        "Idempotency-Key",
                        "gold-leave-cross-" + UUID.randomUUID());
        assertThat(crossLevelJoin.statusCode()).isEqualTo(409);
        JsonNode rejection = json(crossLevelJoin.body());
        assertThat(rejection.path("code").asText()).isEqualTo("GOLD_QUEUING");
        assertThat(rejection.path("detail").asText()).isEqualTo("加入失败，玩家仍在队列中");

        // 原版 PlayerLeaveRequest：取消匹配后占位解除、空房回收。
        HttpResponse<String> leave =
                post(
                        "/api/v1/gold-rooms/games/30400/leave",
                        LEAVE_NOVICE,
                        player.accessToken(),
                        null,
                        null);
        assertThat(leave.statusCode()).isEqualTo(200);
        assertThat(json(leave.body()).path("code").asText()).isEqualTo("GOLD_LEFT");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select status from game_rooms where room_number = ?",
                                String.class,
                                roomNumber))
                .isEqualTo("DISSOLVED");

        // 重复 leave 幂等成功。
        assertThat(
                        post(
                                        "/api/v1/gold-rooms/games/30400/leave",
                                        LEAVE_NOVICE,
                                        player.accessToken(),
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(200);

        // leave 后玩家可自由加入其他场次。
        HttpResponse<String> nextJoin =
                post(
                        "/api/v1/gold-rooms/games/30400/join",
                        JOIN_ADVANCED,
                        player.accessToken(),
                        "Idempotency-Key",
                        "gold-leave-next-" + UUID.randomUUID());
        assertThat(nextJoin.statusCode()).isEqualTo(202);
        assertThat(json(nextJoin.body()).path("status").asText()).isEqualTo("MATCHING");
    }

    @Test
    void leaveIsRejectedOnceTheRoundHasStarted() throws Exception {
        List<Login> players = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            Login player = loginUser();
            setCoins(player.userId(), 20_000L);
            players.add(player);
            HttpResponse<String> response =
                    post(
                            "/api/v1/gold-rooms/games/30400/join",
                            JOIN_NOVICE,
                            player.accessToken(),
                            "Idempotency-Key",
                            "gold-leave-gaming-" + UUID.randomUUID());
            assertThat(response.statusCode()).isEqualTo(202);
        }

        HttpResponse<String> leave =
                post(
                        "/api/v1/gold-rooms/games/30400/leave",
                        LEAVE_NOVICE,
                        players.getFirst().accessToken(),
                        null,
                        null);
        assertThat(leave.statusCode()).isEqualTo(409);
        JsonNode body = json(leave.body());
        assertThat(body.path("code").asText()).isEqualTo("GOLD_GAMING");
        assertThat(body.path("detail").asText()).isEqualTo("牌局已开始，请回到牌局继续游戏");
    }

    @Test
    void ownerLeaveTransfersOwnershipToTheRemainingPlayer() throws Exception {
        Login owner = loginUser();
        setCoins(owner.userId(), 40_000L);
        Login joiner = loginUser();
        setCoins(joiner.userId(), 40_000L);
        post(
                "/api/v1/gold-rooms/games/30400/join",
                JOIN_NOVICE,
                owner.accessToken(),
                "Idempotency-Key",
                "gold-owner-" + UUID.randomUUID());
        HttpResponse<String> joinerJoin =
                post(
                        "/api/v1/gold-rooms/games/30400/join",
                        JOIN_NOVICE,
                        joiner.accessToken(),
                        "Idempotency-Key",
                        "gold-owner-joiner-" + UUID.randomUUID());
        assertThat(joinerJoin.statusCode()).isEqualTo(202);
        String roomNumber = json(joinerJoin.body()).path("roomNumber").asText();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select owner_user_id from game_rooms where room_number = ?",
                                UUID.class,
                                roomNumber))
                .isEqualTo(owner.userId());

        // 原版服务端队列在房主离场后维护新房主，剩余玩家不再被「房间缺少房主座位」卡死。
        HttpResponse<String> ownerLeave =
                post(
                        "/api/v1/gold-rooms/games/30400/leave",
                        LEAVE_NOVICE,
                        owner.accessToken(),
                        null,
                        null);
        assertThat(ownerLeave.statusCode()).isEqualTo(200);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select owner_user_id from game_rooms where room_number = ?",
                                UUID.class,
                                roomNumber))
                .isEqualTo(joiner.userId());
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select status from game_rooms where room_number = ?",
                                String.class,
                                roomNumber))
                .isEqualTo("OPEN");
    }

    @Test
    void matchPollingSeesDissolvedSnapshotAfterTheRoomIsRecycled() throws Exception {
        Login player = loginUser();
        setCoins(player.userId(), 40_000L);
        HttpResponse<String> join =
                post(
                        "/api/v1/gold-rooms/games/30400/join",
                        JOIN_NOVICE,
                        player.accessToken(),
                        "Idempotency-Key",
                        "gold-poll-join-" + UUID.randomUUID());
        assertThat(join.statusCode()).isEqualTo(202);
        String roomNumber = json(join.body()).path("roomNumber").asText();

        // 原版 MatchServer 服务端超时回收等待房；等待中的玩家不离开，由 sweep 把房解散。
        jdbcTemplate.update(
                "update game_rooms set created_at = created_at - interval '10 minutes'"
                        + " where room_number = ?",
                roomNumber);
        assertThat(matchService.sweepTimedOutRooms(Instant.now())).isGreaterThanOrEqualTo(1);

        // 安卓 scheduleMatchStatusPoll 靠 phase=DISSOLVED 撤下等待页，轮询不能被打成 409/403。
        HttpResponse<String> snapshot =
                get("/api/v1/game-sessions/" + roomNumber, player.accessToken());
        assertThat(snapshot.statusCode()).isEqualTo(200);
        JsonNode snapshotBody = json(snapshot.body());
        assertThat(snapshotBody.path("phase").asText()).isEqualTo("DISSOLVED");
        assertThat(snapshotBody.path("seats").size()).isZero();
    }

    @Test
    void sweepDissolvesStaleMatchingPlaceholders() throws Exception {
        Login player = loginUser();
        setCoins(player.userId(), 40_000L);
        HttpResponse<String> join =
                post(
                        "/api/v1/gold-rooms/games/30400/join",
                        JOIN_NOVICE,
                        player.accessToken(),
                        "Idempotency-Key",
                        "gold-sweep-join-" + UUID.randomUUID());
        assertThat(join.statusCode()).isEqualTo(202);
        String roomNumber = json(join.body()).path("roomNumber").asText();
        jdbcTemplate.update(
                "update game_rooms set created_at = created_at - interval '10 minutes'"
                        + " where room_number = ?",
                roomNumber);

        // 原版由 MatchServer 服务端队列承担超时清理；这里手动触发等价的 sweep 兑底。
        int dissolved = matchService.sweepTimedOutRooms(Instant.now());

        assertThat(dissolved).isGreaterThanOrEqualTo(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select status from game_rooms where room_number = ?",
                                String.class,
                                roomNumber))
                .isEqualTo("DISSOLVED");
    }
}
