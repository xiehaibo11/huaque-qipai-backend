package com.nanbei.entertainment.backend.mission.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.mission.application.MissionResponses.MissionCatalogResponse;
import com.nanbei.entertainment.backend.mission.application.MissionResponses.MissionMilestoneState;
import com.nanbei.entertainment.backend.mission.application.MissionResponses.MissionMilestoneStatus;
import com.nanbei.entertainment.backend.mission.application.MissionResponses.MissionPageStatus;
import com.nanbei.entertainment.backend.mission.application.MissionResponses.MissionPageSummary;
import com.nanbei.entertainment.backend.mission.application.MissionResponses.MissionReward;
import com.nanbei.entertainment.backend.mission.application.MissionResponses.MissionTaskState;
import com.nanbei.entertainment.backend.mission.application.MissionResponses.MissionTaskStatus;
import com.nanbei.entertainment.backend.mission.application.MissionResponses.MissionWalletSnapshot;
import com.nanbei.entertainment.backend.mission.domain.MissionCycle;
import com.nanbei.entertainment.backend.mission.domain.MissionEventType;
import com.nanbei.entertainment.backend.mission.domain.MissionMilestoneClaimEntity;
import com.nanbei.entertainment.backend.mission.domain.MissionPageDefinitionEntity;
import com.nanbei.entertainment.backend.mission.domain.MissionRewardEntity;
import com.nanbei.entertainment.backend.mission.domain.MissionTaskDefinitionEntity;
import com.nanbei.entertainment.backend.mission.domain.UserMissionProgressEntity;
import com.nanbei.entertainment.backend.mission.infrastructure.MissionMilestoneClaimRepository;
import com.nanbei.entertainment.backend.mission.infrastructure.MissionMilestoneDefinitionRepository;
import com.nanbei.entertainment.backend.mission.infrastructure.MissionPageDefinitionRepository;
import com.nanbei.entertainment.backend.mission.infrastructure.MissionRewardRepository;
import com.nanbei.entertainment.backend.mission.infrastructure.MissionTaskDefinitionRepository;
import com.nanbei.entertainment.backend.mission.infrastructure.UserMissionProgressRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MissionQueryService {
    private final MissionPageDefinitionRepository pageRepository;
    private final MissionTaskDefinitionRepository taskRepository;
    private final MissionMilestoneDefinitionRepository milestoneRepository;
    private final MissionRewardRepository rewardRepository;
    private final UserMissionProgressRepository progressRepository;
    private final MissionMilestoneClaimRepository milestoneClaimRepository;
    private final PlayerWalletRepository walletRepository;
    private final MissionProgressService progressService;
    private final Clock clock;

    public MissionQueryService(
            MissionPageDefinitionRepository pageRepository,
            MissionTaskDefinitionRepository taskRepository,
            MissionMilestoneDefinitionRepository milestoneRepository,
            MissionRewardRepository rewardRepository,
            UserMissionProgressRepository progressRepository,
            MissionMilestoneClaimRepository milestoneClaimRepository,
            PlayerWalletRepository walletRepository,
            MissionProgressService progressService,
            Clock clock) {
        this.pageRepository = pageRepository;
        this.taskRepository = taskRepository;
        this.milestoneRepository = milestoneRepository;
        this.rewardRepository = rewardRepository;
        this.progressRepository = progressRepository;
        this.milestoneClaimRepository = milestoneClaimRepository;
        this.walletRepository = walletRepository;
        this.progressService = progressService;
        this.clock = clock;
    }

    @Transactional
    public MissionCatalogResponse catalog(UUID userId) {
        Instant now = clock.instant();
        List<MissionPageSummary> summaries =
                pageRepository.findByEnabledTrueOrderByDisplayOrderAsc().stream()
                        .map(page -> summarize(userId, page))
                        .toList();
        return new MissionCatalogResponse(now, summaries);
    }

    /** 首屏页 = 目录里 displayOrder 最小的启用页面，对应原版默认选中第一个页签。 */
    @Transactional
    public MissionPageStatus firstPage(UUID userId) {
        MissionPageDefinitionEntity page =
                pageRepository.findByEnabledTrueOrderByDisplayOrderAsc().stream()
                        .findFirst()
                        .orElseThrow(() -> new ApiException(
                                ErrorCode.MISSION_PAGE_NOT_FOUND, "任务页面不存在"));
        recordLogin(userId, page.getPageCode());
        return buildPage(userId, page);
    }

    @Transactional
    public MissionPageStatus page(UUID userId, String requestedPageCode) {
        String pageCode = normalize(requestedPageCode);
        MissionPageDefinitionEntity page = requirePage(pageCode);
        recordLogin(userId, pageCode);
        return buildPage(userId, page);
    }

    MissionPageStatus buildPage(UUID userId, MissionPageDefinitionEntity page) {
        PageComputation current = compute(userId, page);
        List<MissionPageSummary> pages =
                pageRepository.findByEnabledTrueOrderByDisplayOrderAsc().stream()
                        .map(other -> other.getPageCode().equals(page.getPageCode())
                                ? current.summary()
                                : compute(userId, other).summary())
                        .toList();
        MissionWalletSnapshot wallet = walletRepository.findById(userId)
                .map(MissionQueryService::wallet)
                .orElseGet(MissionWalletSnapshot::empty);
        return new MissionPageStatus(
                current.now(),
                current.summary(),
                pages,
                current.activityPoints(),
                current.milestones(),
                current.tasks(),
                wallet);
    }

    private record PageComputation(
            Instant now,
            List<MissionTaskStatus> tasks,
            long activityPoints,
            List<MissionMilestoneStatus> milestones,
            MissionPageSummary summary) {}

    private PageComputation compute(UUID userId, MissionPageDefinitionEntity page) {
        Instant now = clock.instant();
        Instant cycleStart = MissionCycle.start(page.getCycleType(), now);
        Instant cycleEnd = MissionCycle.end(page.getCycleType(), now);
        List<MissionTaskStatus> allTasks =
                taskRepository.findByPageCodeAndEnabledTrueOrderByDisplayOrderAsc(page.getPageCode())
                        .stream()
                        .map(task -> taskStatus(userId, cycleStart, cycleEnd, task))
                        .toList();
        // 活跃值按本周期所有已领取任务累计，不受限时任务下架影响。
        long activityPoints = allTasks.stream()
                .filter(task -> task.state() == MissionTaskState.CLAIMED)
                .mapToLong(MissionTaskStatus::activityPoints)
                .sum();
        List<MissionTaskStatus> taskStatuses = allTasks.stream()
                .filter(task -> visible(task, now))
                .sorted(Comparator.comparingInt(
                                (MissionTaskStatus status) -> stateOrder(status.state()))
                        .thenComparingInt(MissionTaskStatus::displayOrder))
                .toList();
        Set<Long> claimedTargets = claimedTargets(userId, page.getPageCode(), cycleStart);
        List<MissionMilestoneStatus> milestoneStatuses =
                milestoneRepository.findByPageCodeAndEnabledTrueOrderByDisplayOrderAsc(page.getPageCode())
                        .stream()
                        .map(milestone -> new MissionMilestoneStatus(
                                milestone.getTarget(),
                                milestoneState(milestone.getTarget(), activityPoints, claimedTargets),
                                milestone.getDisplayOrder(),
                                milestoneRewards(page.getPageCode(), milestone.getTarget())))
                        .toList();
        boolean redPoint = taskStatuses.stream()
                        .anyMatch(task -> task.state() == MissionTaskState.CLAIMABLE)
                || milestoneStatuses.stream()
                        .anyMatch(item -> item.state() == MissionMilestoneState.CLAIMABLE);
        MissionPageSummary summary = new MissionPageSummary(
                page.getPageCode(),
                page.getDisplayName(),
                page.getCycleType(),
                MissionCycle.end(page.getCycleType(), now),
                redPoint);
        return new PageComputation(
                now, taskStatuses, activityPoints, milestoneStatuses, summary);
    }

    MissionPageDefinitionEntity requirePage(String pageCode) {
        return pageRepository.findByPageCodeAndEnabledTrue(pageCode)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.MISSION_PAGE_NOT_FOUND, "任务页面不存在"));
    }

    private MissionPageSummary summarize(UUID userId, MissionPageDefinitionEntity page) {
        recordLogin(userId, page.getPageCode());
        return compute(userId, page).summary();
    }

    private void recordLogin(UUID userId, String pageCode) {
        if ("DAILY".equals(pageCode)) {
            progressService.record(userId, MissionEventType.LOGIN, 1);
        }
    }

    /** View.lua getShowTaskInfo：活动已过期的任务只在待领取且未过领奖宽限时继续显示。 */
    static boolean visible(MissionTaskStatus task, Instant now) {
        if (task.endsAt().isAfter(now)) return true;
        return task.state() == MissionTaskState.CLAIMABLE
                && task.drawDeadline() != null
                && !task.drawDeadline().isBefore(now);
    }

    private MissionTaskStatus taskStatus(
            UUID userId,
            Instant cycleStart,
            Instant cycleEnd,
            MissionTaskDefinitionEntity task) {
        UserMissionProgressEntity progress = progressRepository
                .findByUserIdAndTaskCodeAndCycleStartedAt(userId, task.getTaskCode(), cycleStart)
                .orElse(null);
        long value = progress == null ? 0 : progress.getProgress();
        MissionTaskState state = progress != null && progress.isClaimed()
                ? MissionTaskState.CLAIMED
                : value >= task.getTarget()
                        ? MissionTaskState.CLAIMABLE
                        : MissionTaskState.IN_PROGRESS;
        List<MissionReward> rewards = rewardRepository
                .findByTaskCodeOrderByRewardOrderAsc(task.getTaskCode()).stream()
                .map(MissionQueryService::reward)
                .toList();
        Instant startsAt = task.getStartsAt() == null ? cycleStart : task.getStartsAt();
        Instant endsAt = task.getEndsAt() == null ? cycleEnd : task.getEndsAt();
        return new MissionTaskStatus(
                task.getTaskCode(), task.getDisplayName(), value, task.getTarget(),
                task.getActivityPoints(), state, task.getJumpType(), task.getDisplayOrder(),
                startsAt, endsAt, task.getDrawDeadline(), rewards);
    }

    private List<MissionReward> milestoneRewards(String pageCode, long target) {
        return rewardRepository
                .findByPageCodeAndMilestoneTargetOrderByRewardOrderAsc(pageCode, target)
                .stream()
                .map(MissionQueryService::reward)
                .toList();
    }

    private Set<Long> claimedTargets(UUID userId, String pageCode, Instant cycleStart) {
        Set<Long> result = new HashSet<>();
        for (MissionMilestoneClaimEntity claim :
                milestoneClaimRepository.findByUserIdAndPageCodeAndCycleStartedAt(
                        userId, pageCode, cycleStart)) {
            result.add(claim.getTarget());
        }
        return result;
    }

    private static MissionMilestoneState milestoneState(
            long target, long points, Set<Long> claimedTargets) {
        if (claimedTargets.contains(target)) return MissionMilestoneState.CLAIMED;
        return points >= target ? MissionMilestoneState.CLAIMABLE : MissionMilestoneState.LOCKED;
    }

    private static MissionReward reward(MissionRewardEntity reward) {
        return new MissionReward(
                reward.getItemCode(), reward.getDisplayName(), reward.getAmount(), reward.getIconKey());
    }

    private static MissionWalletSnapshot wallet(PlayerWalletEntity wallet) {
        return new MissionWalletSnapshot(
                wallet.getRoomCards(), wallet.getCoins(), wallet.getDiamonds(), wallet.getCoupons());
    }

    private static int stateOrder(MissionTaskState state) {
        return switch (state) {
            case CLAIMABLE -> 0;
            case IN_PROGRESS -> 1;
            case CLAIMED -> 2;
        };
    }

    private static String normalize(String pageCode) {
        return pageCode == null ? "" : pageCode.trim().toUpperCase(Locale.ROOT);
    }
}
