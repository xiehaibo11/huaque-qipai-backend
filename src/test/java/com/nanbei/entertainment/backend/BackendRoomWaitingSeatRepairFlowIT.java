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
class BackendRoomWaitingSeatRepairFlowIT extends RoomFlowTestSupport {
    @Test
    void replacesAWaitingSeatLeftBehindByThePreviousDeployment() throws Exception {
        Login owner = loginUser();
        setRoomCardCenti(owner.userId(), 2_000);
        String roomNumber = createTaizhouRoom(owner);
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
        int vacatedSeat =
                json(get("/api/v1/game-sessions/" + roomNumber, leaving.accessToken()).body())
                        .path("mySeat")
                        .asInt();

        jdbcTemplate.update(
                """
                delete from room_participants
                where room_id = (select id from game_rooms where room_number = ?)
                  and user_id = ?
                """,
                roomNumber,
                leaving.userId());

        Login replacement = loginUser();
        join(roomNumber, replacement);
        JsonNode repaired =
                json(get("/api/v1/game-sessions/" + roomNumber, replacement.accessToken()).body());
        assertThat(repaired.path("mySeat").asInt()).isEqualTo(vacatedSeat);
        assertThat(repaired.path("seats")).hasSize(3);
        assertThat(get("/api/v1/game-sessions/" + roomNumber, leaving.accessToken()).statusCode())
                .isEqualTo(403);
    }

    private String createTaizhouRoom(Login owner) throws Exception {
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
                        "legacy-seat-room-" + UUID.randomUUID());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return json(response.body()).path("roomNumber").asText();
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
}
