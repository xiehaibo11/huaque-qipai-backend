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
class BackendRoomPlacementFlowIT extends RoomFlowTestSupport {
    @Test
    void returnsCurrentBoxPlacementAndIncludesItInCreationConflict() throws Exception {
        Login owner = loginUser();
        setRoomCardCenti(owner.userId(), 200);

        JsonNode empty = json(get("/api/v1/rooms/current", owner.accessToken()).body());
        assertThat(empty.path("inRoom").asBoolean()).isFalse();

        JsonNode created = createRoom(owner);
        String roomNumber = created.path("roomNumber").asText();
        JsonNode current = json(get("/api/v1/rooms/current", owner.accessToken()).body());
        assertPlacement(current, roomNumber, 900038L, 30588L, true);

        HttpResponse<String> conflict =
                post(
                        "/api/v1/rooms",
                        defaultWulongRequest(),
                        owner.accessToken(),
                        "Idempotency-Key",
                        "room-" + UUID.randomUUID());
        assertThat(conflict.statusCode()).isEqualTo(409);
        JsonNode problem = json(conflict.body());
        assertThat(problem.path("code").asText()).isEqualTo("ROOM_ALREADY_OPEN");
        assertPlacement(problem.path("placement"), roomNumber, 900038L, 30588L, true);

        assertThat(
                        post(
                                        "/api/v1/rooms/" + roomNumber + "/dissolve",
                                        "{}",
                                        owner.accessToken(),
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(200);
        assertThat(
                        json(get("/api/v1/rooms/current", owner.accessToken()).body())
                                .path("inRoom")
                                .asBoolean())
                .isFalse();
    }

    @Test
    void letsOnlyANonOwnerLeaveAnOpenRoom() throws Exception {
        Login owner = loginUser();
        setRoomCardCenti(owner.userId(), 200);
        String roomNumber = createRoom(owner).path("roomNumber").asText();
        Login participant = loginUser();

        assertThat(
                        post(
                                        "/api/v1/rooms/" + roomNumber + "/join",
                                        "{}",
                                        participant.accessToken(),
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(200);
        JsonNode current = json(get("/api/v1/rooms/current", participant.accessToken()).body());
        assertPlacement(current, roomNumber, 900038L, 30588L, false);

        HttpResponse<String> ownerLeave =
                post(
                        "/api/v1/rooms/" + roomNumber + "/leave",
                        "{}",
                        owner.accessToken(),
                        null,
                        null);
        assertThat(ownerLeave.statusCode()).isEqualTo(403);
        assertThat(json(ownerLeave.body()).path("code").asText()).isEqualTo("ROOM_FORBIDDEN");

        HttpResponse<String> left =
                post(
                        "/api/v1/rooms/" + roomNumber + "/leave",
                        "{}",
                        participant.accessToken(),
                        null,
                        null);
        assertThat(left.statusCode()).isEqualTo(200);
        assertThat(json(left.body()).path("inRoom").asBoolean()).isFalse();
        assertThat(
                        json(get("/api/v1/rooms/current", participant.accessToken()).body())
                                .path("inRoom")
                                .asBoolean())
                .isFalse();
    }

    @Test
    void leavingAWaitingGameplaySessionRemovesTheSeatAndLetsAReplacementReuseIt()
            throws Exception {
        Login owner = loginUser();
        setRoomCardCenti(owner.userId(), 2_000);
        String roomNumber = createTaizhouRoom(owner).path("roomNumber").asText();
        Login leaving = loginUser();
        Login staying = loginUser();
        join(roomNumber, leaving);
        join(roomNumber, staying);

        assertThat(
                        post(
                                        "/api/v1/game-sessions/" + roomNumber,
                                        "{}",
                                        owner.accessToken(),
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(201);
        JsonNode before =
                json(get("/api/v1/game-sessions/" + roomNumber, leaving.accessToken()).body());
        int vacatedSeat = before.path("mySeat").asInt();

        assertThat(
                        post(
                                        "/api/v1/rooms/" + roomNumber + "/leave",
                                        "{}",
                                        leaving.accessToken(),
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(200);
        assertThat(get("/api/v1/game-sessions/" + roomNumber, leaving.accessToken()).statusCode())
                .isEqualTo(403);

        Login replacement = loginUser();
        join(roomNumber, replacement);
        JsonNode replaced =
                json(get("/api/v1/game-sessions/" + roomNumber, replacement.accessToken()).body());
        assertThat(replaced.path("mySeat").asInt()).isEqualTo(vacatedSeat);
        assertThat(replaced.path("seats")).hasSize(3);
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                select count(*)
                                from game_session_seats seat
                                join game_sessions session on session.id = seat.session_id
                                join game_rooms room on room.id = session.room_id
                                where room.room_number = ? and seat.user_id = ?
                                """,
                                Long.class,
                                roomNumber,
                                leaving.userId()))
                .isZero();
    }

    @Test
    void keepsGoldRoomsOutOfPlacementAndRejectsJoiningTwoActiveBoxRooms() throws Exception {
        Login goldOwner = loginUser();
        setRoomCardCenti(goldOwner.userId(), 400);
        String formerBox = createRoom(goldOwner).path("roomNumber").asText();
        jdbcTemplate.update(
                "update game_rooms set venue = 'GOLD' where room_number = ?", formerBox);

        assertThat(
                        json(get("/api/v1/rooms/current", goldOwner.accessToken()).body())
                                .path("inRoom")
                                .asBoolean())
                .isFalse();
        assertThat(createRoom(goldOwner).path("roomNumber").asText()).isNotEqualTo(formerBox);

        Login firstOwner = loginUser();
        Login secondOwner = loginUser();
        setRoomCardCenti(firstOwner.userId(), 200);
        setRoomCardCenti(secondOwner.userId(), 200);
        String firstRoom = createRoom(firstOwner).path("roomNumber").asText();
        String secondRoom = createRoom(secondOwner).path("roomNumber").asText();
        Login participant = loginUser();
        assertThat(
                        post(
                                        "/api/v1/rooms/" + firstRoom + "/join",
                                        "{}",
                                        participant.accessToken(),
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(200);

        HttpResponse<String> secondJoin =
                post(
                        "/api/v1/rooms/" + secondRoom + "/join",
                        "{}",
                        participant.accessToken(),
                        null,
                        null);
        assertThat(secondJoin.statusCode()).isEqualTo(409);
        JsonNode problem = json(secondJoin.body());
        assertThat(problem.path("code").asText()).isEqualTo("ROOM_ALREADY_OPEN");
        assertPlacement(problem.path("placement"), firstRoom, 900038L, 30588L, false);
    }

    private JsonNode createRoom(Login owner) throws Exception {
        HttpResponse<String> response =
                post(
                        "/api/v1/rooms",
                        defaultWulongRequest(),
                        owner.accessToken(),
                        "Idempotency-Key",
                        "room-" + UUID.randomUUID());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return json(response.body());
    }

    private JsonNode createTaizhouRoom(Login owner) throws Exception {
        HttpResponse<String> response =
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
                        owner.accessToken(),
                        "Idempotency-Key",
                        "taizhou-room-" + UUID.randomUUID());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return json(response.body());
    }

    private void join(String roomNumber, Login participant) throws Exception {
        assertThat(
                        post(
                                        "/api/v1/rooms/" + roomNumber + "/join",
                                        "{}",
                                        participant.accessToken(),
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(200);
    }

    private static void assertPlacement(
            JsonNode placement,
            String roomNumber,
            long lobbyId,
            long gameId,
            boolean owner) {
        assertThat(placement.path("inRoom").asBoolean()).isTrue();
        assertThat(placement.path("roomNumber").asText()).isEqualTo(roomNumber);
        assertThat(placement.path("lobbyId").asLong()).isEqualTo(lobbyId);
        assertThat(placement.path("gameId").asLong()).isEqualTo(gameId);
        assertThat(placement.path("gameRuleDisplay").asText()).isNotBlank();
        assertThat(placement.path("playerCount").asInt()).isEqualTo(4);
        assertThat(placement.path("playCount").asInt()).isEqualTo(4);
        assertThat(placement.path("owner").asBoolean()).isEqualTo(owner);
    }
}
