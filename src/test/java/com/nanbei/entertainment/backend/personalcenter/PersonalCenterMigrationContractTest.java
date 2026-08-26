package com.nanbei.entertainment.backend.personalcenter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PersonalCenterMigrationContractTest {
    @Test
    void migrationCreatesOwnedPrivacyAndFeedbackTables()
            throws Exception {
        String sql =
                Files.readString(
                        Path.of(
                                "src/main/resources/db/migration/"
                                        + "V6__personal_center_preferences_feedback.sql"));

        assertThat(sql).contains("CREATE TABLE user_privacy_settings");
        assertThat(sql).contains("user_id UUID PRIMARY KEY");
        assertThat(sql).contains("REFERENCES app_users(id)");
        assertThat(sql).contains("CREATE TABLE user_feedback");
        assertThat(sql).contains("content VARCHAR(500)");
        assertThat(sql).contains("idx_user_feedback_user_created");
    }

    @Test
    void migrationPersistsTheZhejiangClipboardPermissionSwitch()
            throws Exception {
        String sql =
                Files.readString(
                        Path.of(
                                "src/main/resources/db/migration/"
                                        + "V39__personal_center_clipboard_preference.sql"));

        assertThat(sql).contains("ALTER TABLE user_privacy_settings");
        assertThat(sql).contains("clipboard_access_enabled BOOLEAN NOT NULL DEFAULT TRUE");
    }
}
