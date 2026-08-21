package com.nanbei.entertainment.backend.gameplay;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GameplayMigrationContractTest {
    @Test
    void migrationPersistsRevisionedSessionsPrivateEventsAndCommandIdempotency()
            throws Exception {
        Path migration =
                Path.of("src/main/resources/db/migration/V28__gameplay_foundation.sql");
        String sql = Files.readString(migration, StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("CREATE TABLE game_sessions")
                .contains("room_id UUID NOT NULL UNIQUE REFERENCES game_rooms(id) ON DELETE RESTRICT")
                .contains("state JSONB NOT NULL DEFAULT '{}'::jsonb")
                .contains("CHECK (revision >= 0)")
                .contains("CREATE TABLE game_session_seats")
                .contains("PRIMARY KEY (session_id, seat_number)")
                .contains("UNIQUE (session_id, user_id)")
                .contains("CREATE TABLE game_commands")
                .contains("UNIQUE (user_id, idempotency_key)")
                .contains("CREATE TABLE game_events")
                .contains("UNIQUE (session_id, revision, event_order)")
                .contains("CHECK (visibility IN ('PUBLIC', 'SEAT'))")
                .contains("CHECK ((visibility = 'PUBLIC' AND target_seat IS NULL)")
                .contains("CREATE TABLE game_round_results")
                .contains("UNIQUE (session_id, round_number)")
                .doesNotContain("ON DELETE CASCADE")
                .doesNotContain("INSERT INTO");
    }
}
