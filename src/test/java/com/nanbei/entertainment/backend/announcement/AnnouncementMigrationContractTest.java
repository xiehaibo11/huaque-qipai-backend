package com.nanbei.entertainment.backend.announcement;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AnnouncementMigrationContractTest {
    @Test
    void migrationAddsVersionedAnnouncementContentAndPerUserReads() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V47__announcement_center.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).contains("ALTER TABLE lobby_announcements");
        assertThat(sql).contains("title VARCHAR(200)");
        assertThat(sql).contains("subtitle VARCHAR(300)");
        assertThat(sql).contains("body_text TEXT");
        assertThat(sql).contains("page_url VARCHAR(2048)");
        assertThat(sql).contains("version BIGINT");
        assertThat(sql).contains("CREATE TABLE announcement_reads");
        assertThat(sql).contains("user_id UUID NOT NULL REFERENCES app_users(id)");
        assertThat(sql).contains("announcement_version BIGINT NOT NULL");
        assertThat(sql).contains("PRIMARY KEY (user_id, announcement_id)");
        assertThat(sql).contains("page_url LIKE 'https://%'");
        assertThat(sql).contains("body_text IS NOT NULL AND page_url IS NULL");
        assertThat(sql).contains("body_text IS NULL AND page_url IS NOT NULL");
        assertThat(sql).doesNotContain("INSERT INTO lobby_announcements");
    }
}
