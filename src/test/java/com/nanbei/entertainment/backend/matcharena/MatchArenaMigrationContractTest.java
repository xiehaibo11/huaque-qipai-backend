package com.nanbei.entertainment.backend.matcharena;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MatchArenaMigrationContractTest {
    @Test
    void migrationDefinesPersistentArenaMembershipFundingAndSixDigitNumber() throws Exception {
        Path migration =
                Path.of("src/main/resources/db/migration/V20__match_arena.sql");
        String sql = Files.readString(migration, StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("CREATE SEQUENCE match_arena_number_seq")
                .contains("START WITH 100000")
                .contains("MAXVALUE 999999")
                .contains("NO CYCLE")
                .contains("CREATE TABLE match_arenas")
                .contains("CREATE TABLE match_arena_members")
                .contains("CREATE TABLE match_arena_card_ledger")
                .contains("UNIQUE (owner_user_id, idempotency_key)")
                .contains("CHECK (arena_number BETWEEN 100000 AND 999999)")
                .contains("CHECK (room_card_centi >= 0)")
                .contains("CHECK (visible_to_strangers)")
                .contains("'LEGACY', 'JUNIOR', 'INTERMEDIATE', 'SENIOR'")
                .contains("CHECK (auto_transfer_threshold = 50)")
                .contains("CHECK (NOT auto_transfer_enabled OR auto_transfer_amount > 0)")
                .contains("INITIAL_FUNDING")
                .contains("ON DELETE RESTRICT");
    }
}
