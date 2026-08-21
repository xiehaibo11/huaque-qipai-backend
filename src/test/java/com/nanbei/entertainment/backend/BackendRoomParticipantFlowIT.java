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
class BackendRoomParticipantFlowIT extends RoomFlowTestSupport {
    @Test
    void requiresAFullRoomAndChargesEveryAaParticipantInCenti() throws Exception {
        Login owner = loginUser();
        setRoomCardCenti(owner.userId(), 25);
        JsonNode created =
                json(
                        post(
                                        "/api/v1/rooms",
                                        defaultWulongAaRequest(),
                                        owner.accessToken(),
                                        "Idempotency-Key",
                                        "room-" + UUID.randomUUID())
                                .body());
        String roomNumber = created.path("roomNumber").asText();
        assertThat(created.path("roomFeeCenti").asInt()).isEqualTo(25);

        HttpResponse<String> notFull =
                post(
                        "/api/v1/rooms/" + roomNumber + "/first-round",
                        "{}",
                        owner.accessToken(),
                        null,
                        null);
        assertThat(notFull.statusCode()).isEqualTo(409);
        assertThat(json(notFull.body()).path("code").asText()).isEqualTo("ROOM_NOT_FULL");
        assertThat(roomCardCenti(owner.userId())).isEqualTo(25);

        Login participant1 = loginUser();
        Login participant2 = loginUser();
        Login participant3 = loginUser();
        List<Login> participants = List.of(participant1, participant2, participant3);
        participants.forEach(login -> setRoomCardCenti(login.userId(), 25));
        for (Login participant : participants) {
            HttpResponse<String> joined =
                    post(
                            "/api/v1/rooms/" + roomNumber + "/join",
                            "{}",
                            participant.accessToken(),
                            null,
                            null);
            assertThat(joined.statusCode()).isEqualTo(200);
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

        Login excess = loginUser();
        setRoomCardCenti(excess.userId(), 25);
        HttpResponse<String> full =
                post(
                        "/api/v1/rooms/" + roomNumber + "/join",
                        "{}",
                        excess.accessToken(),
                        null,
                        null);
        assertThat(full.statusCode()).isEqualTo(409);
        assertThat(json(full.body()).path("code").asText()).isEqualTo("ROOM_FULL");

        HttpResponse<String> started =
                post(
                        "/api/v1/rooms/" + roomNumber + "/first-round",
                        "{}",
                        owner.accessToken(),
                        null,
                        null);
        assertThat(started.statusCode()).isEqualTo(200);
        for (Login login : List.of(owner, participant1, participant2, participant3)) {
            assertThat(roomCardCenti(login.userId())).isZero();
        }
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from room_card_ledger where room_id = (select id from game_rooms where room_number = ?)",
                                Long.class,
                                roomNumber))
                .isEqualTo(4L);

        HttpResponse<String> dissolved =
                post(
                        "/api/v1/rooms/" + roomNumber + "/dissolve",
                        "{}",
                        owner.accessToken(),
                        null,
                        null);
        assertThat(dissolved.statusCode()).isEqualTo(200);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from room_card_ledger where room_id = (select id from game_rooms where room_number = ?)",
                                Long.class,
                                roomNumber))
                .isEqualTo(4L);
    }

    @Test
    void rejectsAaJoinWithoutBalanceAndRollsBackWhenABalanceChangesBeforeStart()
            throws Exception {
        Login owner = loginUser();
        setRoomCardCenti(owner.userId(), 25);
        String roomNumber =
                json(
                                post(
                                                "/api/v1/rooms",
                                                defaultWulongAaRequest(),
                                                owner.accessToken(),
                                                "Idempotency-Key",
                                                "room-" + UUID.randomUUID())
                                        .body())
                        .path("roomNumber")
                        .asText();

        Login participant1 = loginUser();
        HttpResponse<String> insufficientJoin =
                post(
                        "/api/v1/rooms/" + roomNumber + "/join",
                        "{}",
                        participant1.accessToken(),
                        null,
                        null);
        assertThat(insufficientJoin.statusCode()).isEqualTo(409);
        assertThat(json(insufficientJoin.body()).path("code").asText())
                .isEqualTo("ROOM_INSUFFICIENT_BALANCE");

        Login participant2 = loginUser();
        Login participant3 = loginUser();
        for (Login participant : List.of(participant1, participant2, participant3)) {
            setRoomCardCenti(participant.userId(), 25);
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
        setRoomCardCenti(participant3.userId(), 0);

        HttpResponse<String> failedStart =
                post(
                        "/api/v1/rooms/" + roomNumber + "/first-round",
                        "{}",
                        owner.accessToken(),
                        null,
                        null);
        assertThat(failedStart.statusCode()).isEqualTo(409);
        assertThat(json(failedStart.body()).path("code").asText())
                .isEqualTo("ROOM_INSUFFICIENT_BALANCE");
        assertThat(roomCardCenti(owner.userId())).isEqualTo(25);
        assertThat(roomCardCenti(participant1.userId())).isEqualTo(25);
        assertThat(roomCardCenti(participant2.userId())).isEqualTo(25);
        assertThat(roomCardCenti(participant3.userId())).isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from room_card_ledger where room_id = (select id from game_rooms where room_number = ?)",
                                Long.class,
                                roomNumber))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select status from game_rooms where room_number = ?",
                                String.class,
                                roomNumber))
                .isEqualTo("OPEN");
    }
}
