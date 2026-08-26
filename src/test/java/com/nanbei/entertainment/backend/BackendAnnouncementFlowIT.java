package com.nanbei.entertainment.backend;

import static org.assertj.core.api.Assertions.assertThat;

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
class BackendAnnouncementFlowIT extends BackendFlowTestSupport {
    @Test
    void bearerFlowFiltersByLobbyAndPersistsVersionedReads() throws Exception {
        assertThat(get("/api/v1/announcements", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/announcements/1", null).statusCode()).isEqualTo(401);
        assertThat(post("/api/v1/announcements/1/read", "", null, null, null).statusCode())
                .isEqualTo(401);

        String phone = "13800138521";
        String token = login(phone);
        UUID userId = userIdByPhone(phone);
        jdbcTemplate.update("delete from announcement_reads where user_id = ?", userId);
        jdbcTemplate.update("delete from lobby_announcements");
        Instant now = Instant.now();
        long globalId = insertAnnouncement(
                null, "全局公告", "维护说明", "今晚不停服更新", null, 2,
                now.minusSeconds(60), now.plusSeconds(3600), 1);
        long pageId = insertAnnouncement(
                900023L, "台州活动", "详情", null, "https://example.com/taizhou", 1,
                null, null, 3);
        insertAnnouncement(
                900038L, "丽水公告", "不可见", "其他大厅", null, 0,
                null, null, 1);

        JsonNode list = json(get("/api/v1/announcements", token).body());
        assertThat(list.path("lobbyId").asLong()).isEqualTo(900023L);
        assertThat(list.path("announcements").size()).isEqualTo(2);
        assertThat(list.path("announcements").get(0).path("announcementId").asLong())
                .isEqualTo(pageId);
        assertThat(list.path("announcements").get(1).path("announcementId").asLong())
                .isEqualTo(globalId);
        assertThat(list.path("announcements").get(1).path("read").asBoolean()).isFalse();

        JsonNode detail = json(get("/api/v1/announcements/" + globalId, token).body());
        assertThat(detail.path("bodyText").asText()).isEqualTo("今晚不停服更新");
        assertThat(detail.path("version").asLong()).isEqualTo(1);
        assertThat(detail.path("read").asBoolean()).isTrue();
        assertThat(readVersion(userId, globalId)).isEqualTo(1);

        jdbcTemplate.update(
                "update lobby_announcements set body_text = ?, version = 2 where id = ?",
                "公告内容已更新",
                globalId);
        JsonNode changed = json(get("/api/v1/announcements", token).body());
        assertThat(changed.path("announcements").get(1).path("read").asBoolean()).isFalse();
        JsonNode marked = json(post(
                "/api/v1/announcements/" + globalId + "/read", "", token, null, null).body());
        assertThat(marked.path("version").asLong()).isEqualTo(2);
        assertThat(marked.path("read").asBoolean()).isTrue();
        assertThat(readVersion(userId, globalId)).isEqualTo(2);

        JsonNode page = json(get("/api/v1/announcements/" + pageId, token).body());
        assertThat(page.path("bodyText").isMissingNode()).isTrue();
        assertThat(page.path("pageUrl").asText()).isEqualTo("https://example.com/taizhou");
    }

    private String login(String phone) throws Exception {
        post("/api/v1/auth/otp/request", "{\"phoneNumber\":\"" + phone + "\"}",
                null, null, null);
        return json(post(
                "/api/v1/auth/otp/verify",
                "{\"phoneNumber\":\"" + phone + "\",\"code\":\"246810\"}",
                null,
                null,
                null).body()).path("accessToken").asText();
    }

    private UUID userIdByPhone(String phone) {
        return jdbcTemplate.queryForObject(
                "select user_id from user_identities where provider = 'PHONE'"
                        + " and provider_subject = ?",
                UUID.class,
                phone);
    }

    private long insertAnnouncement(
            Long lobbyId,
            String title,
            String subtitle,
            String body,
            String pageUrl,
            int sortOrder,
            Instant startsAt,
            Instant endsAt,
            long version) {
        return jdbcTemplate.queryForObject(
                "insert into lobby_announcements"
                        + " (content, title, subtitle, body_text, page_url, lobby_id, sort_order,"
                        + " enabled, starts_at, ends_at, version, created_at, updated_at)"
                        + " values (?, ?, ?, ?, ?, ?, ?, true, ?, ?, ?, now(), now()) returning id",
                Long.class,
                body == null ? title : body,
                title,
                subtitle,
                body,
                pageUrl,
                lobbyId,
                sortOrder,
                startsAt == null ? null : java.sql.Timestamp.from(startsAt),
                endsAt == null ? null : java.sql.Timestamp.from(endsAt),
                version);
    }

    private long readVersion(UUID userId, long announcementId) {
        return jdbcTemplate.queryForObject(
                "select announcement_version from announcement_reads"
                        + " where user_id = ? and announcement_id = ?",
                Long.class,
                userId,
                announcementId);
    }
}
