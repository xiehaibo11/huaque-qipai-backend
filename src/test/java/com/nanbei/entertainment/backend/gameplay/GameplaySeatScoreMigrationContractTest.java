package com.nanbei.entertainment.backend.gameplay;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GameplaySeatScoreMigrationContractTest {
    @Test
    void migrationAddsAnAuthoritativeInitialScoreToExistingAndNewSeats() throws Exception {
        Path migration =
                Path.of("src/main/resources/db/migration/V31__gameplay_seat_score.sql");

        assertThat(migration).exists();
        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        assertThat(sql)
                .contains("ALTER TABLE game_session_seats")
                .contains("ADD COLUMN score BIGINT NOT NULL DEFAULT 1000");
    }
}
