package com.nanbei.entertainment.backend.friend;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FriendMigrationContractTest {
    @Test
    void migrationCreatesFriendTablesAndPresenceColumn() throws Exception {
        String sql =
                Files.readString(
                        Path.of(
                                "src/main/resources/db/migration/"
                                        + "V8__friend_system.sql"));

        assertThat(sql).contains("ALTER TABLE app_users");
        assertThat(sql).contains("ADD COLUMN last_active_at TIMESTAMPTZ");
        assertThat(sql).contains("CREATE TABLE friendships");
        assertThat(sql).contains("PRIMARY KEY (user_id, friend_id)");
        assertThat(sql).contains("CREATE TABLE friend_applications");
        assertThat(sql)
                .contains(
                        "CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED'))");
        assertThat(sql).contains("uk_friend_application_pending");
        assertThat(sql).contains("WHERE status = 'PENDING'");
        assertThat(sql).contains("CREATE TABLE friend_notifications");
        assertThat(sql).contains("CHECK (type IN ('INVITE', 'RESERVE'))");
        assertThat(sql).contains("idx_friend_notifications_user");
    }
}
