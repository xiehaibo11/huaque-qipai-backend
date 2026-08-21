package com.nanbei.entertainment.backend.timeloginact.application;

import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginActivityEntity;
import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginClaimEntity;
import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginRewardStatus;
import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginSlotEntity;
import com.nanbei.entertainment.backend.timeloginact.domain.TimeLoginSlotSchedule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 某个用户在某个活动自然日内的权威状态快照：时段顺序、每段状态、当日已领次数
 * 与转盘可用次数。全部由服务端判定，客户端不参与。
 */
public record TimeLoginDayState(
        TimeLoginActivityEntity activity,
        LocalDate activityDate,
        int daySecond,
        List<TimeLoginSlotEntity> orderedSlots,
        List<TimeLoginRewardStatus> statuses,
        int slotClaimCount,
        int wheelDrawCount) {

    public static TimeLoginDayState of(
            TimeLoginActivityEntity activity,
            List<TimeLoginSlotEntity> slots,
            List<TimeLoginClaimEntity> claimsToday,
            Instant now) {
        int boundary = activity.getDayBoundarySecond();
        List<TimeLoginSlotEntity> ordered =
                TimeLoginSlotSchedule.sortedByStart(
                        slots, TimeLoginSlotEntity::getStartSecond,
                        TimeLoginSlotEntity::getEndSecond);
        Set<UUID> claimedSlotIds = new HashSet<>();
        int wheelDraws = 0;
        for (TimeLoginClaimEntity claim : claimsToday) {
            if (claim.isSlotClaim()) {
                claimedSlotIds.add(claim.getSlotId());
            } else {
                wheelDraws++;
            }
        }
        Set<Integer> claimedIndexes = new HashSet<>();
        List<int[]> bounds = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            TimeLoginSlotEntity slot = ordered.get(index);
            bounds.add(new int[] {slot.getStartSecond(), slot.getEndSecond()});
            if (claimedSlotIds.contains(slot.getId())) {
                claimedIndexes.add(index);
            }
        }
        int daySecond = TimeLoginSlotSchedule.daySecond(now, boundary);
        List<TimeLoginRewardStatus> statuses =
                TimeLoginSlotSchedule.statuses(
                        bounds, claimedIndexes, daySecond, activity.getSupplementCount());
        return new TimeLoginDayState(
                activity,
                TimeLoginSlotSchedule.activityDate(now, boundary),
                daySecond,
                ordered,
                statuses,
                claimedIndexes.size(),
                wheelDraws);
    }

    public int indexOf(UUID slotId) {
        for (int index = 0; index < orderedSlots.size(); index++) {
            if (orderedSlots.get(index).getId().equals(slotId)) {
                return index;
            }
        }
        return -1;
    }

    public TimeLoginRewardStatus statusAt(int index) {
        return statuses.get(index);
    }

    /**
     * 当前累计的转盘进度。南北娱乐自建规则：每消耗一次抽奖机会扣掉一整轮阈值，
     * 原版服务端如何回收 {@code curCnt} 属未闭合项（见证据台账第 9 节
     * {@code WHEEL_POOL_AND_ODDS}）。
     */
    public int wheelProgress() {
        return Math.max(0, slotClaimCount - wheelDrawCount * activity.getWheelUnlockCount());
    }

    public boolean wheelUnlocked() {
        return wheelProgress() >= activity.getWheelUnlockCount();
    }
}
