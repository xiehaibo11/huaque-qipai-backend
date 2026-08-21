package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Import(BackendFlowTestcontainersConfiguration.class)
class BackendMissionFlowIT extends BackendFlowTestSupport {
    @Autowired DataSource dataSource;

    @Test
    void bearerMissionFlowMatchesOriginalDailyShapeAndClaimsOnce() throws Exception {
        assertThat(get("/api/v1/missions", null).statusCode()).isEqualTo(401);

        String phoneNumber = "13800138321";
        String accessToken = login(phoneNumber);
        UUID userId = userIdByPhone(phoneNumber);

        HttpResponse<String> catalogResponse = get("/api/v1/missions", accessToken);
        assertThat(catalogResponse.statusCode()).isEqualTo(200);
        JsonNode catalog = json(catalogResponse.body());
        assertThat(catalog.path("pages").size()).isEqualTo(2);
        assertThat(catalog.path("pages").get(0).path("pageCode").asText())
                .isEqualTo("DAILY");
        assertThat(catalog.path("pages").get(0).path("redPoint").asBoolean())
                .isTrue();

        JsonNode daily = json(get("/api/v1/missions/pages/DAILY", accessToken).body());
        assertThat(daily.path("page").path("displayName").asText()).isEqualTo("每日任务");
        assertThat(daily.path("activityPoints").asLong()).isZero();
        assertThat(daily.path("milestones").get(0).path("target").asLong()).isEqualTo(800);
        assertThat(daily.path("tasks").get(0).path("taskCode").asText())
                .isEqualTo("DAILY_LOGIN");
        assertThat(daily.path("tasks").get(0).path("state").asText())
                .isEqualTo("CLAIMABLE");

        String key = "mission-login-" + UUID.randomUUID();
        HttpResponse<String> claimed = post(
                "/api/v1/missions/tasks/DAILY_LOGIN/claim",
                "",
                accessToken,
                "Idempotency-Key",
                key);
        assertThat(claimed.statusCode()).isEqualTo(200);
        JsonNode claimedBody = json(claimed.body());
        assertThat(claimedBody.path("activityPoints").asLong()).isEqualTo(400);
        assertThat(claimedBody.path("wallet").path("coins").asLong()).isEqualTo(300);

        JsonNode replay = json(post(
                        "/api/v1/missions/tasks/DAILY_LOGIN/claim",
                        "",
                        accessToken,
                        "Idempotency-Key",
                        key)
                .body());
        assertThat(replay.path("wallet").path("coins").asLong()).isEqualTo(300);
        assertThat(walletCoins(userId)).isEqualTo(300);

        HttpResponse<String> secondClaim = post(
                "/api/v1/missions/tasks/DAILY_LOGIN/claim",
                "",
                accessToken,
                "Idempotency-Key",
                "mission-login-second-" + UUID.randomUUID());
        assertThat(secondClaim.statusCode()).isEqualTo(409);
        assertThat(json(secondClaim.body()).path("code").asText())
                .isEqualTo("MISSION_TASK_ALREADY_CLAIMED");
    }

    @Test
    void serializesConcurrentFirstUseOfTheSameClaimKey() throws Exception {
        String phoneNumber = "13800138322";
        String accessToken = login(phoneNumber);
        UUID userId = userIdByPhone(phoneNumber);
        assertThat(get("/api/v1/missions/pages/DAILY", accessToken).statusCode())
                .isEqualTo(200);
        String key = "mission-concurrent-" + UUID.randomUUID();

        List<HttpResponse<String>> responses;
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            PostgresLockContention.lockAdvisoryKey(
                    blocker, "mission-claim:" + userId + ":" + key);
            responses = ConcurrentTestRequests.run(
                    2,
                    () -> post(
                            "/api/v1/missions/tasks/DAILY_LOGIN/claim",
                            "",
                            accessToken,
                            "Idempotency-Key",
                            key),
                    () -> {
                        assertThat(PostgresLockContention.awaitDatabaseLockWaiters(
                                        jdbcTemplate, 2, Duration.ofSeconds(2)))
                                .isTrue();
                        blocker.commit();
                    },
                    Duration.ofSeconds(10));
        }

        assertThat(responses).allSatisfy(
                response -> assertThat(response.statusCode()).isEqualTo(200));
        assertThat(responses.stream().map(HttpResponse::body).distinct()).hasSize(1);
        assertThat(walletCoins(userId)).isEqualTo(300);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from mission_claim_requests where user_id = ? and idempotency_key = ?",
                        Long.class,
                        userId,
                        key))
                .isEqualTo(1L);
    }

    private String login(String phoneNumber) throws Exception {
        assertThat(post(
                        "/api/v1/auth/otp/request",
                        "{\"phoneNumber\":\"" + phoneNumber + "\"}",
                        null,
                        null,
                        null)
                .statusCode()).isEqualTo(202);
        return json(post(
                        "/api/v1/auth/otp/verify",
                        "{\"phoneNumber\":\"" + phoneNumber
                                + "\",\"code\":\"246810\"}",
                        null,
                        null,
                        null)
                .body()).path("accessToken").asText();
    }

    private UUID userIdByPhone(String phoneNumber) {
        return jdbcTemplate.queryForObject(
                "select user_id from user_identities where provider = 'PHONE' and provider_subject = ?",
                UUID.class,
                phoneNumber);
    }

    private long walletCoins(UUID userId) {
        return jdbcTemplate.queryForObject(
                "select coins from player_wallets where user_id = ?", Long.class, userId);
    }
}
