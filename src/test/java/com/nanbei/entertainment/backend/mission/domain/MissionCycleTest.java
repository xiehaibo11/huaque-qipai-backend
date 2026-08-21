package com.nanbei.entertainment.backend.mission.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MissionCycleTest {
    @Test
    void dailyCycleResetsAtFourInChina() {
        Instant beforeReset = Instant.parse("2026-08-05T19:59:59Z");

        assertThat(MissionCycle.start(MissionCycleType.DAILY, beforeReset))
                .isEqualTo(Instant.parse("2026-08-04T20:00:00Z"));
        assertThat(MissionCycle.start(MissionCycleType.DAILY, beforeReset.plusSeconds(1)))
                .isEqualTo(Instant.parse("2026-08-05T20:00:00Z"));
    }

    @Test
    void weeklyCycleResetsMondayAtFourInChina() {
        Instant beforeReset = Instant.parse("2026-08-02T19:59:59Z");

        assertThat(MissionCycle.start(MissionCycleType.WEEKLY, beforeReset))
                .isEqualTo(Instant.parse("2026-07-26T20:00:00Z"));
        assertThat(MissionCycle.start(MissionCycleType.WEEKLY, beforeReset.plusSeconds(1)))
                .isEqualTo(Instant.parse("2026-08-02T20:00:00Z"));
    }

    @Test
    void progressIsCappedAtTargetAndNeverDecreases() {
        UserMissionProgressEntity progress =
                new UserMissionProgressEntity(
                        UUID.randomUUID(),
                        "DAILY_PLAY_3",
                        Instant.parse("2026-08-04T20:00:00Z"),
                        3);

        progress.increment(10);
        progress.increment(1);

        assertThat(progress.getProgress()).isEqualTo(3);
        assertThat(progress.isComplete()).isTrue();
    }
}
