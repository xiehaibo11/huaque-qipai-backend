package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Import(BackendFlowTestcontainersConfiguration.class)
class BackendScoreLedgerFlowIT extends BackendFlowTestSupport {
    @Test
    void bearerFlowPersistsAuthoritativeScoreLedgerLifecycleAndQueries() throws Exception {
        UUID unknown = UUID.randomUUID();
        assertThat(post("/api/v1/score-ledgers", "{}", null, null, null).statusCode())
                .isEqualTo(401);
        assertThat(get("/api/v1/score-ledgers/in-progress", null).statusCode())
                .isEqualTo(401);
        assertThat(get("/api/v1/score-ledgers/history", null).statusCode())
                .isEqualTo(401);
        assertThat(get("/api/v1/score-ledgers/" + unknown, null).statusCode())
                .isEqualTo(401);
        assertThat(get("/api/v1/score-ledgers/statistics/monthly", null).statusCode())
                .isEqualTo(401);
        assertThat(post("/api/v1/score-ledgers/" + unknown + "/rounds", "{}",
                        null, null, null).statusCode())
                .isEqualTo(401);
        assertThat(post("/api/v1/score-ledgers/" + unknown + "/end", "",
                        null, null, null).statusCode())
                .isEqualTo(401);
        assertThat(put("/api/v1/score-ledgers/" + unknown + "/favorite",
                        "{\"favorite\":true}", null).statusCode())
                .isEqualTo(401);
        assertThat(delete("/api/v1/score-ledgers/" + unknown, null).statusCode())
                .isEqualTo(401);

        String ownerToken = login("13800138531");
        UUID ownerId = userIdByPhone("13800138531");
        String strangerToken = login("13800138532");

        HttpResponse<String> tooMany = post(
                "/api/v1/score-ledgers",
                "{\"players\":["
                        + "{\"name\":\"1\",\"ownerPlayer\":true},"
                        + "{\"name\":\"2\",\"ownerPlayer\":false},"
                        + "{\"name\":\"3\",\"ownerPlayer\":false},"
                        + "{\"name\":\"4\",\"ownerPlayer\":false},"
                        + "{\"name\":\"5\",\"ownerPlayer\":false},"
                        + "{\"name\":\"6\",\"ownerPlayer\":false},"
                        + "{\"name\":\"7\",\"ownerPlayer\":false}]}",
                ownerToken, null, null);
        assertThat(tooMany.statusCode()).isEqualTo(400);
        assertThat(json(tooMany.body()).path("code").asText())
                .isEqualTo("SCORE_LEDGER_INVALID");

        JsonNode created = json(post(
                "/api/v1/score-ledgers",
                "{\"players\":["
                        + "{\"name\":\" WhimSeeker \",\"ownerPlayer\":true},"
                        + "{\"name\":\"牌友\",\"ownerPlayer\":false}]}",
                ownerToken, null, null).body());
        UUID ledgerId = UUID.fromString(created.path("ledgerId").asText());
        UUID selfId = UUID.fromString(created.path("players").get(0).path("playerId").asText());
        UUID friendId = UUID.fromString(created.path("players").get(1).path("playerId").asText());
        assertThat(created.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(created.path("players").get(0).path("name").asText())
                .isEqualTo("WhimSeeker");
        assertThat(created.path("roundCount").asInt()).isZero();

        HttpResponse<String> nonZero = post(
                "/api/v1/score-ledgers/" + ledgerId + "/rounds",
                roundBody(selfId, 10, friendId, -9), ownerToken, null, null);
        assertThat(nonZero.statusCode()).isEqualTo(400);
        assertThat(json(nonZero.body()).path("code").asText())
                .isEqualTo("SCORE_LEDGER_INVALID");
        assertThat(roundCount(ledgerId)).isZero();
        assertThat(totalScore(selfId)).isZero();

        JsonNode first = json(post(
                "/api/v1/score-ledgers/" + ledgerId + "/rounds",
                roundBody(selfId, 18, friendId, -18), ownerToken, null, null).body());
        assertThat(first.path("roundNumber").asInt()).isEqualTo(1);
        assertThat(first.path("scores").get(0).path("totalAfter").asLong())
                .isEqualTo(18);
        JsonNode second = json(post(
                "/api/v1/score-ledgers/" + ledgerId + "/rounds",
                roundBody(selfId, -5, friendId, 5), ownerToken, null, null).body());
        assertThat(second.path("roundNumber").asInt()).isEqualTo(2);
        assertThat(second.path("scores").get(0).path("totalAfter").asLong())
                .isEqualTo(13);
        assertThat(roundCount(ledgerId)).isEqualTo(2);
        assertThat(totalScore(selfId)).isEqualTo(13);
        assertThat(totalScore(friendId)).isEqualTo(-13);

        assertNotFound(get("/api/v1/score-ledgers/" + ledgerId, strangerToken));
        assertNotFound(post(
                "/api/v1/score-ledgers/" + ledgerId + "/rounds",
                roundBody(selfId, 0, friendId, 0), strangerToken, null, null));
        assertNotFound(put(
                "/api/v1/score-ledgers/" + ledgerId + "/favorite",
                "{\"favorite\":true}", strangerToken));
        assertNotFound(delete("/api/v1/score-ledgers/" + ledgerId, strangerToken));

        JsonNode active = json(get("/api/v1/score-ledgers/in-progress", ownerToken).body());
        assertThat(active.path("ledgers").size()).isEqualTo(1);
        assertThat(active.path("ledgers").get(0).path("roundCount").asInt()).isEqualTo(2);
        assertThat(json(get("/api/v1/score-ledgers/in-progress", strangerToken).body())
                        .path("ledgers").size())
                .isZero();

        JsonNode detail = json(get("/api/v1/score-ledgers/" + ledgerId, ownerToken).body());
        assertThat(detail.path("rounds").size()).isEqualTo(2);
        assertThat(detail.path("rounds").get(1).path("scores").get(0)
                        .path("scoreDelta").asLong())
                .isEqualTo(-5);

        JsonNode favorite = json(put(
                "/api/v1/score-ledgers/" + ledgerId + "/favorite",
                "{\"favorite\":true}", ownerToken).body());
        assertThat(favorite.path("favorite").asBoolean()).isTrue();
        JsonNode unfavorite = json(put(
                "/api/v1/score-ledgers/" + ledgerId + "/favorite",
                "{\"favorite\":false}", ownerToken).body());
        assertThat(unfavorite.path("favorite").asBoolean()).isFalse();

        JsonNode ended = json(post(
                "/api/v1/score-ledgers/" + ledgerId + "/end", "",
                ownerToken, null, null).body());
        assertThat(ended.path("status").asText()).isEqualTo("ENDED");
        assertThat(ended.path("roundCount").asInt()).isEqualTo(2);
        HttpResponse<String> afterEnd = post(
                "/api/v1/score-ledgers/" + ledgerId + "/rounds",
                roundBody(selfId, 0, friendId, 0), ownerToken, null, null);
        assertThat(afterEnd.statusCode()).isEqualTo(409);
        assertThat(json(afterEnd.body()).path("code").asText())
                .isEqualTo("SCORE_LEDGER_ILLEGAL_STATE");

        assertThat(json(get("/api/v1/score-ledgers/in-progress", ownerToken).body())
                        .path("ledgers").size())
                .isZero();
        JsonNode history = json(get(
                "/api/v1/score-ledgers/history?page=1&pageSize=10", ownerToken).body());
        assertThat(history.path("page").asInt()).isEqualTo(1);
        assertThat(history.path("totalCount").asLong()).isEqualTo(1);
        assertThat(history.path("ledgers").get(0).path("players").get(0)
                        .path("totalScore").asLong())
                .isEqualTo(13);

        YearMonth month = YearMonth.now(ZoneId.of("Asia/Shanghai"));
        JsonNode monthly = json(get(
                "/api/v1/score-ledgers/statistics/monthly?month=" + month,
                ownerToken).body());
        assertThat(monthly.path("month").asText()).isEqualTo(month.toString());
        assertThat(monthly.path("totalPlay").asInt()).isEqualTo(1);
        assertThat(monthly.path("winPlay").asInt()).isEqualTo(1);
        assertThat(monthly.path("totalScore").asLong()).isEqualTo(13);
        assertThat(monthly.path("winScore").asLong()).isEqualTo(13);
        assertThat(monthly.path("lossScore").asLong()).isZero();
        assertThat(monthly.path("winMax").asText()).isEqualTo("牌友");
        assertThat(monthly.path("lostMax").asText()).isEqualTo("牌友");

        JsonNode deleted = json(delete(
                "/api/v1/score-ledgers/" + ledgerId, ownerToken).body());
        assertThat(deleted.path("ledgerId").asText()).isEqualTo(ledgerId.toString());
        assertThat(deleted.path("deletedAt").isMissingNode()).isFalse();
        assertNotFound(get("/api/v1/score-ledgers/" + ledgerId, ownerToken));
        assertThat(json(get("/api/v1/score-ledgers/history", ownerToken).body())
                        .path("totalCount").asLong())
                .isZero();
        assertThat(deletedAtPresent(ledgerId)).isTrue();
        assertThat(ownerOf(ledgerId)).isEqualTo(ownerId);
    }

    private void assertNotFound(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(json(response.body()).path("code").asText())
                .isEqualTo("SCORE_LEDGER_NOT_FOUND");
    }

    private String login(String phone) throws Exception {
        assertThat(post("/api/v1/auth/otp/request",
                        "{\"phoneNumber\":\"" + phone + "\"}",
                        null, null, null).statusCode())
                .isEqualTo(202);
        return json(post(
                "/api/v1/auth/otp/verify",
                "{\"phoneNumber\":\"" + phone + "\",\"code\":\"246810\"}",
                null, null, null).body()).path("accessToken").asText();
    }

    private UUID userIdByPhone(String phone) {
        return jdbcTemplate.queryForObject(
                "select user_id from user_identities where provider = 'PHONE'"
                        + " and provider_subject = ?",
                UUID.class,
                phone);
    }

    private static String roundBody(
            UUID firstId, long first, UUID secondId, long second) {
        return "{\"scores\":[{\"playerId\":\"" + firstId
                + "\",\"scoreDelta\":" + first + "},{\"playerId\":\""
                + secondId + "\",\"scoreDelta\":" + second + "}]}";
    }

    private int roundCount(UUID ledgerId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from score_ledger_rounds where ledger_id = ?",
                Integer.class,
                ledgerId);
    }

    private long totalScore(UUID playerId) {
        return jdbcTemplate.queryForObject(
                "select total_score from score_ledger_players where id = ?",
                Long.class,
                playerId);
    }

    private boolean deletedAtPresent(UUID ledgerId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select deleted_at is not null from score_ledgers where id = ?",
                Boolean.class,
                ledgerId));
    }

    private UUID ownerOf(UUID ledgerId) {
        return jdbcTemplate.queryForObject(
                "select owner_user_id from score_ledgers where id = ?",
                UUID.class,
                ledgerId);
    }
}
