package com.nanbei.entertainment.backend.mission.application;

import com.nanbei.entertainment.backend.mission.domain.MissionCycleType;
import java.time.Instant;
import java.util.List;

public final class MissionResponses {
    private MissionResponses() {}

    public enum MissionTaskState { CLAIMABLE, IN_PROGRESS, CLAIMED }
    public enum MissionMilestoneState { LOCKED, CLAIMABLE, CLAIMED }

    public record MissionCatalogResponse(Instant serverTime, List<MissionPageSummary> pages) {}

    public record MissionPageSummary(
            String pageCode,
            String displayName,
            MissionCycleType cycleType,
            Instant expiresAt,
            boolean redPoint) {}

    /**
     * {@code pages} 是原版 LuckyMissionView initTabs/onEventFlushAct 需要的整份页签目录，
     * 让客户端在打开任意一页时都能画出每个页签各自的红点。
     */
    public record MissionPageStatus(
            Instant serverTime,
            MissionPageSummary page,
            List<MissionPageSummary> pages,
            long activityPoints,
            List<MissionMilestoneStatus> milestones,
            List<MissionTaskStatus> tasks,
            MissionWalletSnapshot wallet) {}

    /**
     * {@code startsAt}/{@code endsAt} 永远是权威的绝对时间：限时任务用自己的窗口，
     * 普通任务回落到页签周期，客户端据此判定是否显示 KW_LEFT_TIME 角标和倒计时。
     */
    public record MissionTaskStatus(
            String taskCode,
            String title,
            long progress,
            long target,
            long activityPoints,
            MissionTaskState state,
            String jumpType,
            int displayOrder,
            Instant startsAt,
            Instant endsAt,
            Instant drawDeadline,
            List<MissionReward> rewards) {}

    public record MissionMilestoneStatus(
            long target,
            MissionMilestoneState state,
            int displayOrder,
            List<MissionReward> rewards) {}

    public record MissionReward(
            String code, String displayName, long quantity, String iconKey) {}

    public record MissionWalletSnapshot(
            long roomCards, long coins, long diamonds, long coupons) {
        public static MissionWalletSnapshot empty() {
            return new MissionWalletSnapshot(0, 0, 0, 0);
        }
    }
}
