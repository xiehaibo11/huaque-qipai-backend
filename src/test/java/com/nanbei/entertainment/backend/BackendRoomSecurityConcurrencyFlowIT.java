package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Import(BackendFlowTestcontainersConfiguration.class)
class BackendRoomSecurityConcurrencyFlowIT extends RoomFlowTestSupport {
    @Test
    void requiresAuthenticationForEveryRoomEndpoint() throws Exception {
        assertThat(
                        get(
                                        "/api/v1/rooms/rule-config?lobbyId=900038&gameId=30588",
                                        null)
                                .statusCode())
                .isEqualTo(401);
        assertThat(
                        post(
                                        "/api/v1/rooms",
                                        defaultWulongRequest(),
                                        null,
                                        "Idempotency-Key",
                                        "unauthenticated-room")
                                .statusCode())
                .isEqualTo(401);
        assertThat(get("/api/v1/rooms/123456", null).statusCode()).isEqualTo(401);
        assertThat(post("/api/v1/rooms/123456/join", "{}", null, null, null).statusCode())
                .isEqualTo(401);
        assertThat(
                        post(
                                        "/api/v1/rooms/123456/first-round",
                                        "{}",
                                        null,
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(401);
        assertThat(
                        post(
                                        "/api/v1/rooms/123456/dissolve",
                                        "{}",
                                        null,
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(401);
    }

    @Test
    void enforcesParticipantOwnerActiveRoomAndIdempotencyBoundaries()
            throws Exception {
        Login owner = loginUser();
        setRoomCardCenti(owner.userId(), 400);
        String idempotencyKey = "room-" + UUID.randomUUID();
        JsonNode created =
                json(
                        post(
                                        "/api/v1/rooms",
                                        defaultWulongRequest(),
                                        owner.accessToken(),
                                        "Idempotency-Key",
                                        idempotencyKey)
                                .body());
        String roomNumber = created.path("roomNumber").asText();

        HttpResponse<String> changedPayload =
                post(
                        "/api/v1/rooms",
                        defaultWulongRequest().replace("playCount_4", "playCount_8"),
                        owner.accessToken(),
                        "Idempotency-Key",
                        idempotencyKey);
        assertThat(changedPayload.statusCode()).isEqualTo(409);
        assertThat(json(changedPayload.body()).path("code").asText())
                .isEqualTo("ROOM_IDEMPOTENCY_CONFLICT");

        HttpResponse<String> activeRoom =
                post(
                        "/api/v1/rooms",
                        defaultWulongRequest(),
                        owner.accessToken(),
                        "Idempotency-Key",
                        "room-" + UUID.randomUUID());
        assertThat(activeRoom.statusCode()).isEqualTo(409);
        assertThat(json(activeRoom.body()).path("code").asText())
                .isEqualTo("ROOM_ALREADY_OPEN");

        Login stranger = loginUser();
        assertForbidden(get("/api/v1/rooms/" + roomNumber, stranger.accessToken()));
        assertForbidden(
                post(
                        "/api/v1/rooms/" + roomNumber + "/first-round",
                        "{}",
                        stranger.accessToken(),
                        null,
                        null));
        assertForbidden(
                post(
                        "/api/v1/rooms/" + roomNumber + "/dissolve",
                        "{}",
                        stranger.accessToken(),
                        null,
                        null));
    }

    @Test
    void serializesConcurrentIdempotentCreationForOneOwner() throws Exception {
        Login owner = loginUser();
        setRoomCardCenti(owner.userId(), 100);
        String idempotencyKey = "room-" + UUID.randomUUID();

        List<HttpResponse<String>> responses =
                concurrently(
                        () ->
                                post(
                                        "/api/v1/rooms",
                                        defaultWulongRequest(),
                                        owner.accessToken(),
                                        "Idempotency-Key",
                                        idempotencyKey),
                        () ->
                                post(
                                        "/api/v1/rooms",
                                        defaultWulongRequest(),
                                        owner.accessToken(),
                                        "Idempotency-Key",
                                        idempotencyKey));

        assertThat(responses).extracting(HttpResponse::statusCode).containsOnly(201);
        String first = json(responses.get(0).body()).path("roomNumber").asText();
        String second = json(responses.get(1).body()).path("roomNumber").asText();
        assertThat(first).isEqualTo(second);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from game_rooms where owner_user_id = ?",
                                Long.class,
                                owner.userId()))
                .isEqualTo(1L);
    }

    @Test
    void allocatesDistinctRoomNumbersForConcurrentOwners() throws Exception {
        Login firstOwner = loginUser();
        Login secondOwner = loginUser();
        setRoomCardCenti(firstOwner.userId(), 100);
        setRoomCardCenti(secondOwner.userId(), 100);

        List<HttpResponse<String>> responses =
                concurrently(
                        () ->
                                post(
                                        "/api/v1/rooms",
                                        defaultWulongRequest(),
                                        firstOwner.accessToken(),
                                        "Idempotency-Key",
                                        "room-" + UUID.randomUUID()),
                        () ->
                                post(
                                        "/api/v1/rooms",
                                        defaultWulongRequest(),
                                        secondOwner.accessToken(),
                                        "Idempotency-Key",
                                        "room-" + UUID.randomUUID()));

        assertThat(responses).extracting(HttpResponse::statusCode).containsOnly(201);
        String first = json(responses.get(0).body()).path("roomNumber").asText();
        String second = json(responses.get(1).body()).path("roomNumber").asText();
        assertThat(first).matches("[0-9]{6}");
        assertThat(second).matches("[0-9]{6}").isNotEqualTo(first);
    }

    private void assertForbidden(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(json(response.body()).path("code").asText())
                .isEqualTo("ROOM_FORBIDDEN");
    }

    private List<HttpResponse<String>> concurrently(
            Callable<HttpResponse<String>> first,
            Callable<HttpResponse<String>> second)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<HttpResponse<String>> firstFuture =
                    executor.submit(awaitStart(first, ready, start));
            Future<HttpResponse<String>> secondFuture =
                    executor.submit(awaitStart(second, ready, start));
            ready.await();
            start.countDown();
            return List.of(firstFuture.get(), secondFuture.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private static Callable<HttpResponse<String>> awaitStart(
            Callable<HttpResponse<String>> request,
            CountDownLatch ready,
            CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await();
            return request.call();
        };
    }
}
