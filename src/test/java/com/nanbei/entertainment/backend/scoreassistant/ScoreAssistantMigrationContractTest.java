package com.nanbei.entertainment.backend.scoreassistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ScoreAssistantMigrationContractTest {
    @Test
    void migrationDefinesLedgersPlayersRoundsAndAuthoritativeScoreEntries() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V48__score_assistant.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).contains("CREATE TABLE score_ledgers");
        assertThat(sql).contains("owner_user_id UUID NOT NULL REFERENCES app_users(id)");
        assertThat(sql).contains("status VARCHAR(20) NOT NULL");
        assertThat(sql).contains("round_count INTEGER NOT NULL DEFAULT 0");
        assertThat(sql).contains("CREATE TABLE score_ledger_players");
        assertThat(sql).contains("position SMALLINT NOT NULL CHECK (position BETWEEN 1 AND 6)");
        assertThat(sql).contains("owner_player BOOLEAN NOT NULL");
        assertThat(sql).contains("total_score BIGINT NOT NULL DEFAULT 0");
        assertThat(sql).contains("UNIQUE (ledger_id, display_name)");
        assertThat(sql).contains("CREATE TABLE score_ledger_rounds");
        assertThat(sql).contains("UNIQUE (ledger_id, round_number)");
        assertThat(sql).contains("CREATE TABLE score_ledger_round_scores");
        assertThat(sql).contains("score_delta BIGINT NOT NULL");
        assertThat(sql).contains("total_after BIGINT NOT NULL");
        assertThat(sql).contains("PRIMARY KEY (round_id, player_id)");
        assertThat(sql).doesNotContain("INSERT");
    }
}
