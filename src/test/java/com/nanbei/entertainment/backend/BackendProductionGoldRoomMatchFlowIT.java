package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "nanbei.gameplay.qa.enabled=false")
@ActiveProfiles("local")
@Import(BackendFlowTestcontainersConfiguration.class)
class BackendProductionGoldRoomMatchFlowIT extends RoomFlowTestSupport {
    private static final String JOIN_NOVICE =
            """
            {"lobbyId":900023,"roomNameFlag":1}
            """;

    @Test
    void fourRealPlayersAreSeatedTogetherAndServerAuthorityStartsTheRound() throws Exception {
        List<Login> players = new ArrayList<>();
        List<JsonNode> joins = new ArrayList<>();
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
                            "gold-production-" + UUID.randomUUID());
            assertThat(response.statusCode()).isEqualTo(202);
            joins.add(json(response.body()));
        }

        String roomNumber = joins.getFirst().path("roomNumber").asText();
        assertThat(roomNumber).matches("\\d{6}");
        assertThat(joins).allSatisfy(join -> {
            assertThat(join.path("roomNumber").asText()).isEqualTo(roomNumber);
            assertThat(join.path("roomMode").asText()).isEqualTo("SERVER_AUTHORITY");
        });
        assertThat(joins.getFirst().path("status").asText()).isEqualTo("MATCHING");
        assertThat(joins.getLast().path("status").asText()).isEqualTo("READY");
        assertThat(joins.getLast().path("autoGameplay").asBoolean()).isTrue();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select venue from game_rooms where room_number = ?",
                                String.class,
                                roomNumber))
                .isEqualTo("GOLD");

        Login goldOwner = players.getFirst();
        assertThat(get("/api/v1/rooms/" + roomNumber, goldOwner.accessToken()).statusCode())
                .isEqualTo(404);
        assertThat(
                        post(
                                        "/api/v1/rooms/" + roomNumber + "/first-round",
                                        "{}",
                                        goldOwner.accessToken(),
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(404);
        assertThat(
                        post(
                                        "/api/v1/rooms/" + roomNumber + "/dissolve",
                                        "{}",
                                        goldOwner.accessToken(),
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(404);

        for (Login player : players) {
            assertThat(
                            json(get("/api/v1/rooms/current", player.accessToken()).body())
                                    .path("inRoom")
                                    .asBoolean())
                    .isFalse();
            HttpResponse<String> snapshot =
                    get("/api/v1/game-sessions/" + roomNumber, player.accessToken());
            assertThat(snapshot.statusCode()).as(snapshot.body()).isEqualTo(200);
            JsonNode body = json(snapshot.body());
            assertThat(body.path("phase").asText()).isEqualTo("DEALING");
            assertThat(body.path("seats")).hasSize(4);
            assertThat(body.path("maxPlayCount").asInt()).isEqualTo(1);
            assertThat(body.path("multipleChoice").path("goldMode").asBoolean()).isTrue();
            assertThat(body.path("multipleChoice").path("baseScore").asLong()).isEqualTo(200L);
        }
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                select count(*)
                                from user_identities identity
                                join game_session_seats seat on seat.user_id = identity.user_id
                                join game_sessions session on session.id = seat.session_id
                                join game_rooms room on room.id = session.room_id
                                where room.room_number = ? and identity.provider = 'QA_BOT'
                                """,
                                Long.class,
                                roomNumber))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForList(
                                """
                                select seat.score
                                from game_session_seats seat
                                join game_sessions session on session.id = seat.session_id
                                join game_rooms room on room.id = session.room_id
                                where room.room_number = ?
                                order by seat.seat_number
                                """,
                                Long.class,
                                roomNumber))
                .containsExactly(20_000L, 20_000L, 20_000L, 20_000L);

        jdbcTemplate.update(
                """
                update game_sessions session
                set phase = 'ROUND_RESULT'
                from game_rooms room
                where session.room_id = room.id and room.room_number = ?
                """,
                roomNumber);
        HttpResponse<String> rematchResponse =
                post(
                        "/api/v1/gold-rooms/games/30400/join",
                        JOIN_NOVICE,
                        players.getFirst().accessToken(),
                        "Idempotency-Key",
                        "gold-rematch-" + UUID.randomUUID());
        assertThat(rematchResponse.statusCode()).isEqualTo(202);
        JsonNode rematch = json(rematchResponse.body());
        assertThat(rematch.path("roomNumber").asText()).isNotEqualTo(roomNumber);
        assertThat(rematch.path("status").asText()).isEqualTo("MATCHING");
    }
}
