package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Import(BackendFlowTestcontainersConfiguration.class)
class BackendFriendFlowIT extends BackendFlowTestSupport {
    private static final String PHONE_B = "13800138021";
    private static final String PHONE_A = "13800138022";

    @Test
    void completesFriendLifecycleBetweenTwoUsers() throws Exception {
        String tokenB = login(PHONE_B);
        assertThat(get("/api/v1/home", tokenB).statusCode())
                .isEqualTo(200);
        long publicIdB =
                json(get("/api/v1/users/me", tokenB).body())
                        .path("publicPlayerId")
                        .asLong();
        assertThat(publicIdB).isPositive();

        String tokenA = login(PHONE_A);
        assertThat(get("/api/v1/home", tokenA).statusCode())
                .isEqualTo(200);
        long publicIdA =
                json(get("/api/v1/users/me", tokenA).body())
                        .path("publicPlayerId")
                        .asLong();
        assertThat(publicIdA).isPositive();

        HttpResponse<String> searchById =
                get("/api/v1/friends/search?query=" + publicIdB, tokenA);
        assertThat(searchById.statusCode()).isEqualTo(200);
        JsonNode found = json(searchById.body());
        assertThat(found.path("publicPlayerId").asLong())
                .isEqualTo(publicIdB);
        assertThat(found.path("relation").asText()).isEqualTo("NONE");
        assertThat(searchById.body()).doesNotContain(PHONE_B);

        HttpResponse<String> searchByPhone =
                get("/api/v1/friends/search?query=" + PHONE_B, tokenA);
        assertThat(searchByPhone.statusCode()).isEqualTo(200);
        assertThat(
                        json(searchByPhone.body())
                                .path("publicPlayerId")
                                .asLong())
                .isEqualTo(publicIdB);
        assertThat(searchByPhone.body()).doesNotContain(PHONE_B);

        HttpResponse<String> applied =
                post(
                        "/api/v1/friends/applications",
                        "{\"publicPlayerId\":" + publicIdB + "}",
                        tokenA,
                        null,
                        null);
        assertThat(applied.statusCode()).isEqualTo(202);

        HttpResponse<String> duplicated =
                post(
                        "/api/v1/friends/applications",
                        "{\"publicPlayerId\":" + publicIdB + "}",
                        tokenA,
                        null,
                        null);
        assertThat(duplicated.statusCode()).isEqualTo(409);
        assertThat(json(duplicated.body()).path("code").asText())
                .isEqualTo("FRIEND_APPLICATION_EXISTS");

        JsonNode applications =
                json(get("/api/v1/friends/applications", tokenB).body());
        assertThat(applications.path("total").asInt()).isEqualTo(1);
        JsonNode application = applications.path("applications").get(0);
        assertThat(application.path("publicPlayerId").asLong())
                .isEqualTo(publicIdA);
        String applicationId = application.path("id").asText();

        assertThat(
                        post(
                                        "/api/v1/friends/applications/"
                                                + applicationId
                                                + "/accept",
                                        "{}",
                                        tokenB,
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(200);

        JsonNode friendsOfA =
                json(get("/api/v1/friends", tokenA).body());
        assertThat(friendsOfA.path("friends").size()).isEqualTo(1);
        JsonNode bInAList = friendsOfA.path("friends").get(0);
        assertThat(bInAList.path("publicPlayerId").asLong())
                .isEqualTo(publicIdB);
        assertThat(bInAList.path("state").asText()).isEqualTo("ONLINE");
        assertThat(bInAList.path("lastActiveAt").asText()).isNotBlank();
        assertThat(bInAList.path("shielded").asBoolean()).isFalse();

        JsonNode friendsOfB =
                json(get("/api/v1/friends", tokenB).body());
        assertThat(friendsOfB.path("friends").size()).isEqualTo(1);
        assertThat(
                        friendsOfB.path("friends")
                                .get(0)
                                .path("publicPlayerId")
                                .asLong())
                .isEqualTo(publicIdA);

        assertThat(
                        post(
                                        "/api/v1/friends/"
                                                + publicIdB
                                                + "/invite",
                                        "{\"type\":\"INVITE\"}",
                                        tokenA,
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(202);
        HttpResponse<String> tooFrequent =
                post(
                        "/api/v1/friends/" + publicIdB + "/invite",
                        "{\"type\":\"INVITE\"}",
                        tokenA,
                        null,
                        null);
        assertThat(tooFrequent.statusCode()).isEqualTo(429);
        assertThat(json(tooFrequent.body()).path("code").asText())
                .isEqualTo("FRIEND_INVITE_TOO_FREQUENT");

        JsonNode notifications =
                json(
                        get(
                                        "/api/v1/friends/notifications?unread=true",
                                        tokenB)
                                .body());
        assertThat(notifications.path("total").asInt()).isEqualTo(1);
        JsonNode notification =
                notifications.path("notifications").get(0);
        assertThat(notification.path("type").asText()).isEqualTo("INVITE");
        assertThat(notification.path("actorPublicPlayerId").asLong())
                .isEqualTo(publicIdA);

        assertThat(
                        post(
                                        "/api/v1/friends/notifications/read",
                                        "{}",
                                        tokenB,
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(204);
        assertThat(
                        json(
                                        get(
                                                        "/api/v1/friends/notifications?unread=true",
                                                        tokenB)
                                                .body())
                                .path("total")
                                .asInt())
                .isZero();

        HttpResponse<String> recall =
                post(
                        "/api/v1/friends/" + publicIdB + "/invite",
                        "{\"type\":\"RECALL\"}",
                        tokenA,
                        null,
                        null);
        assertThat(recall.statusCode()).isEqualTo(202);
        HttpResponse<String> recallTooFrequent =
                post(
                        "/api/v1/friends/" + publicIdB + "/invite",
                        "{\"type\":\"RECALL\"}",
                        tokenA,
                        null,
                        null);
        assertThat(recallTooFrequent.statusCode()).isEqualTo(429);
        assertThat(json(recallTooFrequent.body()).path("code").asText())
                .isEqualTo("FRIEND_RECALL_TOO_FREQUENT");

        assertThat(
                        put(
                                        "/api/v1/friends/"
                                                + publicIdB
                                                + "/shield",
                                        "{\"shielded\":true}",
                                        tokenA)
                                .statusCode())
                .isEqualTo(200);
        JsonNode aListAfterShield =
                json(get("/api/v1/friends", tokenA).body());
        assertThat(
                        aListAfterShield.path("friends")
                                .get(0)
                                .path("shielded")
                                .asBoolean())
                .isTrue();
        JsonNode bListAfterShield =
                json(get("/api/v1/friends", tokenB).body());
        assertThat(
                        bListAfterShield.path("friends")
                                .get(0)
                                .path("shielded")
                                .asBoolean())
                .isFalse();

        assertThat(
                        delete("/api/v1/friends/" + publicIdA, tokenB)
                                .statusCode())
                .isEqualTo(200);
        assertThat(
                        json(get("/api/v1/friends", tokenA).body())
                                .path("friends")
                                .size())
                .isZero();
        assertThat(
                        json(get("/api/v1/friends", tokenB).body())
                                .path("friends")
                                .size())
                .isZero();

        JsonNode relationAfterRemoval =
                json(
                        get(
                                        "/api/v1/friends/search?query="
                                                + publicIdB,
                                        tokenA)
                                .body());
        assertThat(relationAfterRemoval.path("relation").asText())
                .isEqualTo("NONE");
    }

    @Test
    void rejectsSelfSearchStrangerInviteAndUnknownPlayer() throws Exception {
        String token = login("13800138023");
        assertThat(get("/api/v1/home", token).statusCode())
                .isEqualTo(200);
        long ownPublicId =
                json(get("/api/v1/users/me", token).body())
                        .path("publicPlayerId")
                        .asLong();

        HttpResponse<String> selfSearch =
                get("/api/v1/friends/search?query=13800138023", token);
        assertThat(selfSearch.statusCode()).isEqualTo(400);
        assertThat(json(selfSearch.body()).path("code").asText())
                .isEqualTo("FRIEND_SELF_OPERATION");

        HttpResponse<String> missing =
                get("/api/v1/friends/search?query=1999999999", token);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(json(missing.body()).path("code").asText())
                .isEqualTo("FRIEND_NOT_FOUND");

        HttpResponse<String> strangerInvite =
                post(
                        "/api/v1/friends/" + ownPublicId + "/invite",
                        "{\"type\":\"INVITE\"}",
                        token,
                        null,
                        null);
        assertThat(strangerInvite.statusCode()).isEqualTo(403);
        assertThat(json(strangerInvite.body()).path("code").asText())
                .isEqualTo("FRIEND_NOT_FRIEND");

        assertThat(get("/api/v1/friends", null).statusCode())
                .isEqualTo(401);
    }

    @Test
    void inviteAllNotifiesOnlineFriendsAndSkipsShieldedAndCooldown()
            throws Exception {
        String tokenA = login("13800138026");
        assertThat(get("/api/v1/home", tokenA).statusCode())
                .isEqualTo(200);
        String tokenB = login("13800138027");
        assertThat(get("/api/v1/home", tokenB).statusCode())
                .isEqualTo(200);
        String tokenC = login("13800138028");
        assertThat(get("/api/v1/home", tokenC).statusCode())
                .isEqualTo(200);
        long publicIdA = publicPlayerId(tokenA);
        long publicIdB = publicPlayerId(tokenB);
        long publicIdC = publicPlayerId(tokenC);
        makeFriends(tokenA, publicIdB, tokenB);
        makeFriends(tokenA, publicIdC, tokenC);

        assertThat(
                        put(
                                        "/api/v1/friends/"
                                                + publicIdC
                                                + "/shield",
                                        "{\"shielded\":true}",
                                        tokenA)
                                .statusCode())
                .isEqualTo(200);

        HttpResponse<String> inviteAll =
                post("/api/v1/friends/invite-all", "{}", tokenA, null, null);
        assertThat(inviteAll.statusCode()).isEqualTo(202);
        JsonNode firstResult = json(inviteAll.body());
        assertThat(firstResult.path("invitedCount").asInt()).isEqualTo(1);
        assertThat(firstResult.path("cooldownSkippedCount").asInt())
                .isZero();

        JsonNode notificationsOfB =
                json(
                        get(
                                        "/api/v1/friends/notifications?unread=true",
                                        tokenB)
                                .body());
        assertThat(notificationsOfB.path("total").asInt()).isEqualTo(1);
        assertThat(
                        notificationsOfB
                                .path("notifications")
                                .get(0)
                                .path("type")
                                .asText())
                .isEqualTo("INVITE");
        assertThat(
                        notificationsOfB
                                .path("notifications")
                                .get(0)
                                .path("actorPublicPlayerId")
                                .asLong())
                .isEqualTo(publicIdA);
        assertThat(
                        json(
                                        get(
                                                        "/api/v1/friends/notifications?unread=true",
                                                        tokenC)
                                                .body())
                                .path("total")
                                .asInt())
                .isZero();

        HttpResponse<String> inviteAllAgain =
                post("/api/v1/friends/invite-all", "{}", tokenA, null, null);
        assertThat(inviteAllAgain.statusCode()).isEqualTo(202);
        JsonNode secondResult = json(inviteAllAgain.body());
        assertThat(secondResult.path("invitedCount").asInt()).isZero();
        assertThat(secondResult.path("cooldownSkippedCount").asInt())
                .isEqualTo(1);
    }

    @Test
    void searchReportsRejectedRelationAfterRejection() throws Exception {
        String tokenA = login("13800138029");
        assertThat(get("/api/v1/home", tokenA).statusCode())
                .isEqualTo(200);
        String tokenB = login("13800138030");
        assertThat(get("/api/v1/home", tokenB).statusCode())
                .isEqualTo(200);
        long publicIdB = publicPlayerId(tokenB);

        assertThat(
                        post(
                                        "/api/v1/friends/applications",
                                        "{\"publicPlayerId\":"
                                                + publicIdB
                                                + "}",
                                        tokenA,
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(202);
        JsonNode applications =
                json(get("/api/v1/friends/applications", tokenB).body());
        String applicationId =
                applications.path("applications").get(0).path("id").asText();
        assertThat(
                        post(
                                        "/api/v1/friends/applications/"
                                                + applicationId
                                                + "/reject",
                                        "{}",
                                        tokenB,
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(200);

        JsonNode relationAfterRejection =
                json(
                        get(
                                        "/api/v1/friends/search?query="
                                                + publicIdB,
                                        tokenA)
                                .body());
        assertThat(relationAfterRejection.path("relation").asText())
                .isEqualTo("REJECTED");
    }

    private long publicPlayerId(String token) throws Exception {
        return json(get("/api/v1/users/me", token).body())
                .path("publicPlayerId")
                .asLong();
    }

    private void makeFriends(String tokenA, long publicIdB, String tokenB)
            throws Exception {
        assertThat(
                        post(
                                        "/api/v1/friends/applications",
                                        "{\"publicPlayerId\":"
                                                + publicIdB
                                                + "}",
                                        tokenA,
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(202);
        JsonNode applications =
                json(get("/api/v1/friends/applications", tokenB).body());
        String applicationId =
                applications.path("applications").get(0).path("id").asText();
        assertThat(
                        post(
                                        "/api/v1/friends/applications/"
                                                + applicationId
                                                + "/accept",
                                        "{}",
                                        tokenB,
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(200);
    }

    private String login(String phoneNumber) throws Exception {
        HttpResponse<String> otpResponse =
                post(
                        "/api/v1/auth/otp/request",
                        "{\"phoneNumber\":\"" + phoneNumber + "\"}",
                        null,
                        null,
                        null);
        assertThat(otpResponse.statusCode()).isEqualTo(202);

        JsonNode tokens =
                json(
                        post(
                                        "/api/v1/auth/otp/verify",
                                        "{\"phoneNumber\":\""
                                                + phoneNumber
                                                + "\",\"code\":\"246810\"}",
                                        null,
                                        null,
                                        null)
                                .body());
        String accessToken = tokens.path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        return accessToken;
    }
}
