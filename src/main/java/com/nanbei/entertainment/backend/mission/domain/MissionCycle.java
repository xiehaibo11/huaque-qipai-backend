package com.nanbei.entertainment.backend.mission.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

public final class MissionCycle {
    public static final ZoneId CHINA = ZoneId.of("Asia/Shanghai");
    public static final LocalTime RESET_TIME = LocalTime.of(4, 0);

    private MissionCycle() {}

    public static Instant start(MissionCycleType cycleType, Instant now) {
        ZonedDateTime shifted = now.atZone(CHINA).minusHours(4);
        var date = shifted.toLocalDate();
        if (cycleType == MissionCycleType.WEEKLY) {
            date = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        return date.atTime(RESET_TIME).atZone(CHINA).toInstant();
    }

    public static Instant end(MissionCycleType cycleType, Instant now) {
        Instant start = start(cycleType, now);
        return cycleType == MissionCycleType.DAILY
                ? start.atZone(CHINA).plusDays(1).toInstant()
                : start.atZone(CHINA).plusWeeks(1).toInstant();
    }
}
