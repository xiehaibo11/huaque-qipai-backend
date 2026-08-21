package com.nanbei.entertainment.backend.membership;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MembershipGoldStatisticsContractTest {
    @Test
    void migrationCreatesGoldStatisticsSnapshots() throws Exception {
        String sql = Files.readString(migration("V14__membership_gold_statistics.sql"));

        assertThat(sql).contains("CREATE TABLE membership_gold_statistics");
        assertThat(sql).contains("user_id UUID NOT NULL REFERENCES app_users(id)");
        assertThat(sql).contains("game_id BIGINT NOT NULL");
        assertThat(sql).contains("played_on DATE NOT NULL");
        assertThat(sql).contains("fight_count INTEGER NOT NULL");
        assertThat(sql).contains("win_count INTEGER NOT NULL");
        assertThat(sql).contains("coin_delta BIGINT NOT NULL");
        assertThat(sql).contains("UNIQUE (user_id, game_id, played_on)");
        assertThat(sql).contains("idx_membership_gold_statistics_user_day");
    }

    @Test
    void controllerExposesOriginalGoldStatisticsContract()
            throws Exception {
        String controller = source("api/MembershipController.java");
        String service = source("application/MembershipGoldStatisticsService.java");
        String response = source("application/MembershipGoldStatisticsStatus.java");

        assertThat(controller).contains("@GetMapping(\"/gold-statistics\")");
        assertThat(controller).contains("MembershipGoldStatisticsStatus");
        assertThat(controller).contains("goldStatisticsService.status");
        assertThat(controller).contains("@RequestParam(defaultValue = \"0\") long gameId");
        assertThat(service).contains("today");
        assertThat(service).contains("yesterday");
        assertThat(service).contains("lastThree");
        assertThat(service).contains("lastSeven");
        assertThat(service).contains("membershipStatusService.isActive(userId)");
        assertThat(response).contains("boolean membershipActive");
        assertThat(response).contains("List<Long> gameId");
        assertThat(response).contains("long fightCnt");
        assertThat(response).contains("int winRate");
        assertThat(response).contains("long winScore");
    }

    private static Path migration(String fileName) {
        return Path.of("src/main/resources/db/migration").resolve(fileName);
    }

    private static String source(String relative) throws Exception {
        return Files.readString(
                Path.of("src/main/java/com/nanbei/entertainment/backend/membership")
                        .resolve(relative));
    }
}
