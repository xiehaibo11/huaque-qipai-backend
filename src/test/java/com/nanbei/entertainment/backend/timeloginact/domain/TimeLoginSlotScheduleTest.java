package com.nanbei.entertainment.backend.timeloginact.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 时段状态推导必须与原版 {@code lobby/Modules/TimeLoginAct} 逐条一致。
 * 用例里的三段目录取自 1.5.4 实机截图：早间 23:00-09:00、午间 09:00-16:00、
 * 晚间 16:00-23:00，与 V36 迁移的种子数据同值。
 */
class TimeLoginSlotScheduleTest {
    private static final int MORNING_START = 82800; // 23:00
    private static final int MORNING_END = 32400; // 09:00
    private static final int NOON_START = 32400;
    private static final int NOON_END = 57600; // 16:00
    private static final int EVENING_START = 57600;
    private static final int EVENING_END = 82800;
    private static final int BOUNDARY = 82800;

    private static final List<int[]> SLOTS =
            List.of(
                    new int[] {MORNING_START, MORNING_END},
                    new int[] {NOON_START, NOON_END},
                    new int[] {EVENING_START, EVENING_END});

    @Test
    void crossMidnightSlotNormalisesItsStartBackByOneDay() {
        assertThat(TimeLoginSlotSchedule.normalizedStartSecond(MORNING_START, MORNING_END))
                .isEqualTo(MORNING_START - 86400);
        assertThat(TimeLoginSlotSchedule.normalizedStartSecond(NOON_START, NOON_END))
                .isEqualTo(NOON_START);
    }

    @Test
    void daySecondShiftsBackAfterTheTwentyThreeHundredBoundary() {
        // 23:30 中国时区 -> 原版 Module.lua:170-172 把 84600 减 86400。
        assertThat(daySecondAt("2026-08-14T15:30:00Z")).isEqualTo(84600 - 86400);
        // 22:30 中国时区仍在当日。
        assertThat(daySecondAt("2026-08-14T14:30:00Z")).isEqualTo(81000);
    }

    @Test
    void activityDateRollsOverAtTwentyThreeHundredNotMidnight() {
        assertThat(activityDateAt("2026-08-14T14:30:00Z")).isEqualTo(LocalDate.of(2026, 8, 14));
        // 23:30 于 8/14 与 00:30 于 8/15 属于同一个活动自然日。
        assertThat(activityDateAt("2026-08-14T15:30:00Z")).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(activityDateAt("2026-08-14T16:30:00Z")).isEqualTo(LocalDate.of(2026, 8, 15));
    }

    @Test
    void middayPutsTheNoonSlotClaimableAndTheEveningSlotPending() {
        // 11:00 中国时区，未领任何时段，补领次数 0。
        List<TimeLoginRewardStatus> statuses =
                TimeLoginSlotSchedule.statuses(SLOTS, Set.of(), 39600, 0);
        assertThat(statuses)
                .containsExactly(
                        TimeLoginRewardStatus.OVER_TIME,
                        TimeLoginRewardStatus.CAN_REWARD,
                        TimeLoginRewardStatus.NOT_IN_TIME);
    }

    @Test
    void supplementCountReopensThePreviousMissedSlot() {
        List<TimeLoginRewardStatus> statuses =
                TimeLoginSlotSchedule.statuses(SLOTS, Set.of(), 39600, 1);
        assertThat(statuses.get(0)).isEqualTo(TimeLoginRewardStatus.CAN_SUPPLE);
        assertThat(statuses.get(1)).isEqualTo(TimeLoginRewardStatus.CAN_REWARD);
    }

    @Test
    void supplementNeverOverwritesAnAlreadyClaimedSlot() {
        List<TimeLoginRewardStatus> statuses =
                TimeLoginSlotSchedule.statuses(SLOTS, Set.of(0), 39600, 1);
        assertThat(statuses.get(0)).isEqualTo(TimeLoginRewardStatus.REWARDED);
    }

    @Test
    void claimedSlotStaysRewardedInsideItsOwnWindow() {
        List<TimeLoginRewardStatus> statuses =
                TimeLoginSlotSchedule.statuses(SLOTS, Set.of(1), 39600, 0);
        assertThat(statuses.get(1)).isEqualTo(TimeLoginRewardStatus.REWARDED);
    }

    @Test
    void justAfterTwentyThreeHundredTheCrossMidnightSlotIsClaimable() {
        int daySecond = daySecondAt("2026-08-14T15:30:00Z");
        List<TimeLoginRewardStatus> statuses =
                TimeLoginSlotSchedule.statuses(SLOTS, Set.of(), daySecond, 0);
        assertThat(statuses)
                .containsExactly(
                        TimeLoginRewardStatus.CAN_REWARD,
                        TimeLoginRewardStatus.NOT_IN_TIME,
                        TimeLoginRewardStatus.NOT_IN_TIME);
    }

    @Test
    void afterMidnightTheSameCrossMidnightSlotIsStillClaimable() {
        int daySecond = daySecondAt("2026-08-14T16:30:00Z"); // 00:30 中国时区
        List<TimeLoginRewardStatus> statuses =
                TimeLoginSlotSchedule.statuses(SLOTS, Set.of(), daySecond, 0);
        assertThat(statuses.get(0)).isEqualTo(TimeLoginRewardStatus.CAN_REWARD);
    }

    @Test
    void sortingFollowsTheNormalisedStartSoTheCrossMidnightSlotComesFirst() {
        record Slot(int start, int end, String name) {}
        List<Slot> unordered =
                List.of(
                        new Slot(NOON_START, NOON_END, "noon"),
                        new Slot(EVENING_START, EVENING_END, "evening"),
                        new Slot(MORNING_START, MORNING_END, "morning"));
        List<Slot> ordered =
                TimeLoginSlotSchedule.sortedByStart(unordered, Slot::start, Slot::end);
        assertThat(ordered.stream().map(Slot::name)).containsExactly("morning", "noon", "evening");
    }

    @Test
    void claimableCoversBothCanRewardAndCanSupple() {
        assertThat(TimeLoginSlotSchedule.claimable(TimeLoginRewardStatus.CAN_REWARD)).isTrue();
        assertThat(TimeLoginSlotSchedule.claimable(TimeLoginRewardStatus.CAN_SUPPLE)).isTrue();
        assertThat(TimeLoginSlotSchedule.claimable(TimeLoginRewardStatus.NOT_IN_TIME)).isFalse();
        assertThat(TimeLoginSlotSchedule.claimable(TimeLoginRewardStatus.OVER_TIME)).isFalse();
        assertThat(TimeLoginSlotSchedule.claimable(TimeLoginRewardStatus.REWARDED)).isFalse();
    }

    @Test
    void wireValuesMatchTheOriginalLuaLiterals() {
        assertThat(TimeLoginRewardStatus.CAN_REWARD.wireValue()).isEqualTo("CanReward");
        assertThat(TimeLoginRewardStatus.REWARDED.wireValue()).isEqualTo("Rewarded");
        assertThat(TimeLoginRewardStatus.CAN_SUPPLE.wireValue()).isEqualTo("CanSupple");
        assertThat(TimeLoginRewardStatus.NOT_IN_TIME.wireValue()).isEqualTo("NotInTime");
        assertThat(TimeLoginRewardStatus.OVER_TIME.wireValue()).isEqualTo("OverTime");
        assertThat(TimeLoginClaimFlag.SUCCESS.wireValue()).isEqualTo("Success");
        assertThat(TimeLoginClaimFlag.NOT_IN_TIME.wireValue()).isEqualTo("Not_In_Time");
        assertThat(TimeLoginClaimFlag.ALREADY_CLAIM.wireValue()).isEqualTo("Already_Claim");
        assertThat(TimeLoginClaimFlag.GOLD_OVER.wireValue()).isEqualTo("Gold_Over");
        assertThat(TimeLoginClaimFlag.WHEEL_CNT_LACK.wireValue()).isEqualTo("Wheel_Cnt_Lack");
        assertThat(TimeLoginClaimFlag.FAILED.wireValue()).isEqualTo("Failed");
    }

    private static int daySecondAt(String instant) {
        return TimeLoginSlotSchedule.daySecond(Instant.parse(instant), BOUNDARY);
    }

    private static LocalDate activityDateAt(String instant) {
        return TimeLoginSlotSchedule.activityDate(Instant.parse(instant), BOUNDARY);
    }
}
