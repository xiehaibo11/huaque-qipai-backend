package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Import(BackendFlowTestcontainersConfiguration.class)
class BackendMailFlowIT extends BackendFlowTestSupport {
    @Test
    void bearerMailFlowSummarizesReadsDeletesAndClaimsOnce() throws Exception {
        assertThat(get("/api/v1/mails", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/mails/summary", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/mails/1", null).statusCode()).isEqualTo(401);
        assertThat(post("/api/v1/mails/read-all", "", null, null, null).statusCode())
                .isEqualTo(401);
        assertThat(
                        post("/api/v1/mails/delete", "{\"mailIds\":[1]}", null, null, null)
                                .statusCode())
                .isEqualTo(401);
        assertThat(post("/api/v1/mails/claim", "{\"mailIds\":[1]}", null, null, null)
                        .statusCode())
                .isEqualTo(401);

        String phoneNumber = "13800138431";
        String accessToken = login(phoneNumber);
        UUID userId = userIdByPhone(phoneNumber);

        JsonNode emptyList = json(get("/api/v1/mails", accessToken).body());
        assertThat(emptyList.path("mails").size()).isZero();
        JsonNode emptySummary = json(get("/api/v1/mails/summary", accessToken).body());
        assertThat(emptySummary.path("unreadCount").asLong()).isZero();
        assertThat(emptySummary.path("awardCount").asLong()).isZero();

        Instant now = Instant.now();
        long plainId =
                insertMail(
                        userId, "系统公告", "[]",
                        now.minusSeconds(3600), null, false, false, false);
        long awardId =
                insertMail(
                        userId, "补偿奖励",
                        "[{\"icon\":\"mail_coin\",\"rewardType\":\"COIN\",\"amount\":100,"
                                + "\"description\":\"金币补偿\"}]",
                        now.minusSeconds(7200), null, false, false, false);
        long readId =
                insertMail(
                        userId, "历史邮件", "[]",
                        now.minusSeconds(10800), null, true, false, false);
        insertMail(
                userId, "过期邮件", "[]",
                now.minusSeconds(20000), now.minusSeconds(100), false, false, false);
        insertMail(
                userId, "已删邮件", "[]",
                now.minusSeconds(30000), null, false, false, true);

        JsonNode summary = json(get("/api/v1/mails/summary", accessToken).body());
        assertThat(summary.path("unreadCount").asLong()).isEqualTo(2);
        assertThat(summary.path("awardCount").asLong()).isEqualTo(1);

        JsonNode list = json(get("/api/v1/mails", accessToken).body());
        assertThat(list.path("mails").size()).isEqualTo(3);
        assertThat(list.path("mails").get(0).path("mailId").asLong()).isEqualTo(plainId);
        assertThat(list.path("mails").get(0).path("title").asText()).isEqualTo("系统公告");
        assertThat(list.path("mails").get(0).path("read").asBoolean()).isFalse();
        assertThat(list.path("mails").get(0).path("hasAttachment").asBoolean()).isFalse();
        assertThat(list.path("mails").get(1).path("mailId").asLong()).isEqualTo(awardId);
        assertThat(list.path("mails").get(1).path("hasAttachment").asBoolean()).isTrue();
        assertThat(list.path("mails").get(2).path("mailId").asLong()).isEqualTo(readId);

        JsonNode detail =
                json(get("/api/v1/mails/" + awardId, accessToken).body());
        assertThat(detail.path("mailId").asLong()).isEqualTo(awardId);
        assertThat(detail.path("sender").asText()).isEqualTo("系统");
        assertThat(detail.path("read").asBoolean()).isTrue();
        assertThat(detail.path("claimed").asBoolean()).isFalse();
        assertThat(detail.path("attachments").size()).isEqualTo(1);
        assertThat(detail.path("attachments").get(0).path("rewardType").asText())
                .isEqualTo("COIN");
        assertThat(detail.path("attachments").get(0).path("amount").asLong())
                .isEqualTo(100);
        Instant firstReadAt = readAtOf(awardId);

        JsonNode detailAgain =
                json(get("/api/v1/mails/" + awardId, accessToken).body());
        assertThat(detailAgain.path("read").asBoolean()).isTrue();
        assertThat(readAtOf(awardId)).isEqualTo(firstReadAt);

        assertThat(get("/api/v1/mails/999999", accessToken).statusCode()).isEqualTo(404);
        assertThat(json(get("/api/v1/mails/999999", accessToken).body())
                        .path("code")
                        .asText())
                .isEqualTo("MAIL_NOT_FOUND");

        JsonNode marked =
                json(post("/api/v1/mails/read-all", "", accessToken, null, null).body());
        assertThat(marked.path("markedCount").asLong()).isEqualTo(1);
        JsonNode markedAgain =
                json(post("/api/v1/mails/read-all", "", accessToken, null, null).body());
        assertThat(markedAgain.path("markedCount").asLong()).isZero();

        JsonNode deleted =
                json(post("/api/v1/mails/delete",
                                "{\"mailIds\":[" + plainId + "," + awardId + "]}",
                                accessToken, null, null)
                        .body());
        assertThat(deleted.path("deletedCount").asLong()).isEqualTo(1);

        JsonNode claimed =
                json(post("/api/v1/mails/claim",
                                "{\"mailIds\":[" + awardId + "]}",
                                accessToken, null, null)
                        .body());
        assertThat(claimed.path("claimedMailIds").size()).isEqualTo(1);
        assertThat(claimed.path("claimedMailIds").get(0).asLong()).isEqualTo(awardId);
        assertThat(claimed.path("rewards").size()).isEqualTo(1);
        assertThat(claimed.path("rewards").get(0).path("rewardType").asText())
                .isEqualTo("COIN");
        assertThat(claimed.path("rewards").get(0).path("amount").asLong()).isEqualTo(100);
        assertThat(claimed.path("wallet").path("coins").asLong()).isEqualTo(100);
        assertThat(walletCoins(userId)).isEqualTo(100);

        JsonNode replay =
                json(post("/api/v1/mails/claim",
                                "{\"mailIds\":[" + awardId + "]}",
                                accessToken, null, null)
                        .body());
        assertThat(replay.path("claimedMailIds").size()).isZero();
        assertThat(replay.path("rewards").size()).isZero();
        assertThat(replay.path("wallet").path("coins").asLong()).isEqualTo(100);
        assertThat(walletCoins(userId)).isEqualTo(100);

        JsonNode deletedAfterClaim =
                json(post("/api/v1/mails/delete",
                                "{\"mailIds\":[" + awardId + "]}",
                                accessToken, null, null)
                        .body());
        assertThat(deletedAfterClaim.path("deletedCount").asLong()).isEqualTo(1);
    }

    @Test
    void hidesForeignMailsFromDetailDeleteAndClaim() throws Exception {
        String ownerPhone = "13800138432";
        String ownerToken = login(ownerPhone);
        UUID ownerId = userIdByPhone(ownerPhone);
        String strangerPhone = "13800138433";
        String strangerToken = login(strangerPhone);

        long foreignId =
                insertMail(
                        ownerId, "他人奖励",
                        "[{\"icon\":\"mail_coin\",\"rewardType\":\"DIAMOND\",\"amount\":50,"
                                + "\"description\":\"钻石\"}]",
                        Instant.now(), null, false, false, false);

        assertThat(get("/api/v1/mails/" + foreignId, ownerToken).statusCode())
                .isEqualTo(200);
        assertThat(get("/api/v1/mails/" + foreignId, strangerToken).statusCode())
                .isEqualTo(404);
        assertThat(json(get("/api/v1/mails/" + foreignId, strangerToken).body())
                        .path("code")
                        .asText())
                .isEqualTo("MAIL_NOT_FOUND");

        JsonNode strangerClaim =
                json(post("/api/v1/mails/claim",
                                "{\"mailIds\":[" + foreignId + "]}",
                                strangerToken, null, null)
                        .body());
        assertThat(strangerClaim.path("claimedMailIds").size()).isZero();

        JsonNode strangerDelete =
                json(post("/api/v1/mails/delete",
                                "{\"mailIds\":[" + foreignId + "]}",
                                strangerToken, null, null)
                        .body());
        assertThat(strangerDelete.path("deletedCount").asLong()).isZero();

        JsonNode ownerList = json(get("/api/v1/mails", ownerToken).body());
        assertThat(ownerList.path("mails").size()).isEqualTo(1);
        JsonNode strangerList = json(get("/api/v1/mails", strangerToken).body());
        assertThat(strangerList.path("mails").size()).isZero();
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
                "select user_id from user_identities where provider = 'PHONE'"
                        + " and provider_subject = ?",
                UUID.class,
                phoneNumber);
    }

    private long insertMail(
            UUID userId,
            String title,
            String attachments,
            Instant sendAt,
            Instant expireAt,
            boolean read,
            boolean claimed,
            boolean deleted) {
        jdbcTemplate.update(
                "insert into mails (user_id, title, intro, content, sender, attachments,"
                        + " send_at, expire_at, read_at, claimed_at, deleted_at, created_at)"
                        + " values (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)",
                userId,
                title,
                "简介",
                "正文",
                "系统",
                attachments,
                java.sql.Timestamp.from(sendAt),
                expireAt == null ? null : java.sql.Timestamp.from(expireAt),
                read ? java.sql.Timestamp.from(sendAt.plusSeconds(60)) : null,
                claimed ? java.sql.Timestamp.from(sendAt.plusSeconds(120)) : null,
                deleted ? java.sql.Timestamp.from(sendAt.plusSeconds(180)) : null,
                java.sql.Timestamp.from(sendAt));
        return jdbcTemplate.queryForObject(
                "select max(id) from mails where user_id = ?", Long.class, userId);
    }

    private Instant readAtOf(long mailId) {
        return jdbcTemplate
                .queryForObject(
                        "select read_at from mails where id = ?",
                        java.sql.Timestamp.class,
                        mailId)
                .toInstant();
    }

    private long walletCoins(UUID userId) {
        return jdbcTemplate.queryForObject(
                "select coins from player_wallets where user_id = ?", Long.class, userId);
    }
}
