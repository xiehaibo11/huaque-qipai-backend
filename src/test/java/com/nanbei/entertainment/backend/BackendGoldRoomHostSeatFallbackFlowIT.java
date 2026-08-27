package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

/**
 * 金币场（goldMode=50）是匹配制场次：进桌、等待轮询与对局快照都不能走亲友房/创建房的
 * 「房主座位」规则。房主早退、转移或旧数据导致 room_participants 不含 owner 时，
 * 剩余玩家进桌与轮询必须返回 200 快照，不得报「房间缺少房主座位」。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "nanbei.gameplay.qa.enabled=false")
@ActiveProfiles("local")
@Import(BackendFlowTestcontainersConfiguration.class)
class BackendGoldRoomHostSeatFallbackFlowIT extends RoomFlowTestSupport {
    private static final String JOIN_NOVICE =
            """
            {"lobbyId":900023,"roomNameFlag":1}
            """;

    @AfterEach
    void cleanGoldRooms() {
        jdbcTemplate.update("delete from game_events");
        jdbcTemplate.update("delete from game_session_seats");
        jdbcTemplate.update("delete from game_sessions");
        jdbcTemplate.update("delete from room_participants");
        jdbcTemplate.update("delete from game_rooms");
    }

    @Test
    void goldMatchKeepsWorkingWhenHostSeatIsGone() throws Exception {
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
                            "gold-host-fallback-" + UUID.randomUUID());
            assertThat(response.statusCode()).isEqualTo(202);
        }
        Login owner = players.getFirst();
        JsonNode ownerJoin =
                json(
                        post(
                                        "/api/v1/gold-rooms/games/30400/join",
                                        JOIN_NOVICE,
                                        owner.accessToken(),
                                        "Idempotency-Key",
                                        "gold-host-fallback-owner-" + UUID.randomUUID())
                                .body());
        String roomNumber = ownerJoin.path("roomNumber").asText();
        UUID roomId =
                jdbcTemplate.queryForObject(
                        "select id from game_rooms where room_number = ?", UUID.class, roomNumber);

        // 等价「房主早退/旧数据」：owner 的 participant 与座位记录消失，剩余 3 人仍在。
        jdbcTemplate.update(
                "delete from room_participants where room_id = ? and user_id = ?",
                roomId,
                owner.userId());
        jdbcTemplate.update(
                """
                delete from game_session_seats
                where session_id = (select id from game_sessions where room_id = ?) and user_id = ?
                """,
                roomId,
                owner.userId());

        // 剩余玩家进桌与等待轮询不得被「房间缺少房主座位」卡死。
        for (Login player : players.subList(1, 4)) {
            HttpResponse<String> snapshot =
                    get("/api/v1/game-sessions/" + roomNumber, player.accessToken());
            assertThat(snapshot.statusCode()).isEqualTo(200);
            JsonNode body = json(snapshot.body());
            assertThat(body.path("phase").asText()).isIn("DEALING", "PLAYING", "ROUND_RESULT");
        }

        // 房主也仍然可以重连快照（对局照常）。
        HttpResponse<String> ownerSnapshot =
                get("/api/v1/game-sessions/" + roomNumber, owner.accessToken());
        assertThat(ownerSnapshot.statusCode()).isEqualTo(200);
    }
}
