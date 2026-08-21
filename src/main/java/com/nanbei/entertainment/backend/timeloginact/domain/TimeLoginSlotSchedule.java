package com.nanbei.entertainment.backend.timeloginact.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 定时登录时段的纯状态推导，逐条对应原版客户端实现：
 *
 * <ul>
 *   <li>跨零点归一：{@code View.lua:137-147} 与 {@code Module.lua:176-181}，
 *       {@code startTime >= endTime} 时 {@code realStartTime -= 86400}。
 *   <li>当日秒数：{@code Module.lua:163-172}，取东八区当日零点起的秒数，
 *       且 {@code daySecond > EVENING} 时减 86400，使活动自然日的切分点落在 23:00。
 *   <li>状态推导：{@code Module.lua:176-195}，命中区间为可领、早于起点为未到时间、
 *       晚于终点且未领为已过期。
 *   <li>补领：{@code Module.lua:200-209}，从当前时段往前 {@code supplementCnt} 段，
 *       未领取的置为可补领。
 * </ul>
 *
 * <p>原版把这套推导放在客户端，只是为了在两次拉取之间就地刷新 UI；本类把同一套规则放到
 * 服务端作为权威判定，客户端仍按服务端下发的 {@code rewardFlag} 渲染。
 */
public final class TimeLoginSlotSchedule {
    public static final ZoneId ACTIVITY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int SECONDS_PER_DAY = 86400;

    private TimeLoginSlotSchedule() {}

    /** 原版 {@code startTime >= endTime} 的跨零点时段起点归一。 */
    public static int normalizedStartSecond(int startSecond, int endSecond) {
        return startSecond >= endSecond ? startSecond - SECONDS_PER_DAY : startSecond;
    }

    /**
     * 活动自然日：{@code dayBoundarySecond} 之后的时间归入次日，与
     * {@code Module.lua:170-172} 把 {@code daySecond} 减 86400 的效果一致。
     */
    public static LocalDate activityDate(Instant now, int dayBoundarySecond) {
        ZonedDateTime local = now.atZone(ACTIVITY_ZONE);
        return local.plusSeconds((long) SECONDS_PER_DAY - dayBoundarySecond).toLocalDate();
    }

    /** 当日秒数，已按 {@code Module.lua:170-172} 做过 23:00 切分。 */
    public static int daySecond(Instant now, int dayBoundarySecond) {
        ZonedDateTime local = now.atZone(ACTIVITY_ZONE);
        int seconds = local.toLocalTime().toSecondOfDay();
        return seconds > dayBoundarySecond ? seconds - SECONDS_PER_DAY : seconds;
    }

    /**
     * 按原版顺序排序：跨零点归一后的起点升序（{@code View.lua:137-147}）。
     * 返回新列表，不改动入参。
     */
    public static <T> List<T> sortedByStart(
            List<T> slots, java.util.function.ToIntFunction<T> start,
            java.util.function.ToIntFunction<T> end) {
        List<T> ordered = new ArrayList<>(slots);
        ordered.sort(
                Comparator.comparingInt(
                        slot ->
                                normalizedStartSecond(
                                        start.applyAsInt(slot), end.applyAsInt(slot))));
        return ordered;
    }

    /**
     * 推导每个时段的状态。{@code slots} 必须已按 {@link #sortedByStart} 排序，
     * {@code claimedIndexes} 是本活动自然日内已领取时段在该顺序里的 0 基下标。
     */
    public static List<TimeLoginRewardStatus> statuses(
            List<int[]> slots, Set<Integer> claimedIndexes, int daySecond, int supplementCount) {
        List<TimeLoginRewardStatus> statuses = new ArrayList<>(slots.size());
        int nowIndex = -1;
        int previousIndex = -1;
        for (int index = 0; index < slots.size(); index++) {
            int startSecond = slots.get(index)[0];
            int endSecond = slots.get(index)[1];
            int realStart = normalizedStartSecond(startSecond, endSecond);
            // 三个分支照搬原版的顺序 if（不是 elseif），后一个会覆盖前一个。
            TimeLoginRewardStatus status =
                    claimedIndexes.contains(index) ? TimeLoginRewardStatus.REWARDED : null;
            if (realStart <= daySecond && daySecond < endSecond) {
                nowIndex = index;
                if (status != TimeLoginRewardStatus.REWARDED) {
                    status = TimeLoginRewardStatus.CAN_REWARD;
                }
            }
            if (daySecond < realStart) {
                // 原版这里是无条件赋值，且每次命中都重写 preIndex，
                // 排序后命中的是连续后缀，因此 preIndex 最终等于最后一个命中下标减一。
                previousIndex = index - 1;
                status = TimeLoginRewardStatus.NOT_IN_TIME;
            }
            if (daySecond >= endSecond && status != TimeLoginRewardStatus.REWARDED) {
                status = TimeLoginRewardStatus.OVER_TIME;
            }
            statuses.add(status == null ? TimeLoginRewardStatus.NOT_IN_TIME : status);
        }
        if (nowIndex == -1 && previousIndex >= 0) {
            nowIndex = previousIndex + 1;
        }
        applySupplement(statuses, nowIndex, supplementCount);
        return statuses;
    }

    /** 原版 {@code Module.lua:200-209}：当前时段往前数 supplementCount 段可补领。 */
    private static void applySupplement(
            List<TimeLoginRewardStatus> statuses, int nowIndex, int supplementCount) {
        if (nowIndex < 0) {
            return;
        }
        for (int index = nowIndex - 1; index >= nowIndex - supplementCount; index--) {
            if (index < 0) {
                return;
            }
            if (statuses.get(index) != TimeLoginRewardStatus.REWARDED) {
                statuses.set(index, TimeLoginRewardStatus.CAN_SUPPLE);
            }
        }
    }

    /** 可领取判定：原版 {@code View.lua:255} 把 CanReward 与 CanSupple 一并放出领取按钮。 */
    public static boolean claimable(TimeLoginRewardStatus status) {
        return status == TimeLoginRewardStatus.CAN_REWARD
                || status == TimeLoginRewardStatus.CAN_SUPPLE;
    }
}
