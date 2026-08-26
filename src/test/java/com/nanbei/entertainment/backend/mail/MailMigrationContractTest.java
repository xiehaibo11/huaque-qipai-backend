package com.nanbei.entertainment.backend.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MailMigrationContractTest {
    @Test
    void migrationDefinesMailsTableColumnsAndIndexes() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V24__mail.sql");
        String sql = new String(Files.readAllBytes(migration), StandardCharsets.UTF_8);

        assertThat(sql).contains("CREATE TABLE mails");
        assertThat(sql).contains("id BIGSERIAL PRIMARY KEY");
        assertThat(sql).contains("user_id UUID NOT NULL REFERENCES app_users(id)");
        assertThat(sql).contains("content TEXT NOT NULL DEFAULT ''");
        assertThat(sql).contains("attachments JSONB NOT NULL DEFAULT '[]'::jsonb");
        assertThat(sql).contains("send_at TIMESTAMPTZ NOT NULL");
        assertThat(sql).contains("expire_at TIMESTAMPTZ,");
        assertThat(sql).contains("read_at TIMESTAMPTZ,");
        assertThat(sql).contains("claimed_at TIMESTAMPTZ,");
        assertThat(sql).contains("deleted_at TIMESTAMPTZ,");
        assertThat(sql).contains("ON mails(user_id, send_at DESC)");
        assertThat(sql)
                .contains("WHERE deleted_at IS NULL AND read_at IS NULL");
        assertThat(sql).doesNotContain("INSERT");
    }

    @Test
    void productionDeliveryMigrationDefinesSourceIdempotencyAndValidityChecks()
            throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V44__mail_delivery_integrity.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).contains("source_type VARCHAR(80)");
        assertThat(sql).contains("source_id VARCHAR(160)");
        assertThat(sql).contains("UNIQUE (user_id, source_type, source_id)");
        assertThat(sql).contains("jsonb_typeof(attachments) = 'array'");
        assertThat(sql).contains("expire_at IS NULL OR expire_at > send_at");
    }
}
