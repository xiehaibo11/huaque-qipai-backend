package com.nanbei.entertainment.backend.mission.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.mission.application.MissionResponses.MissionPageStatus;
import com.nanbei.entertainment.backend.mission.domain.MissionClaimRequestEntity;
import com.nanbei.entertainment.backend.mission.domain.MissionCycle;
import com.nanbei.entertainment.backend.mission.domain.MissionMilestoneClaimEntity;
import com.nanbei.entertainment.backend.mission.domain.MissionMilestoneDefinitionEntity;
import com.nanbei.entertainment.backend.mission.domain.MissionPageDefinitionEntity;
import com.nanbei.entertainment.backend.mission.domain.MissionTaskDefinitionEntity;
import com.nanbei.entertainment.backend.mission.domain.UserMissionProgressEntity;
import com.nanbei.entertainment.backend.mission.infrastructure.MissionClaimRequestRepository;
import com.nanbei.entertainment.backend.mission.infrastructure.MissionMilestoneClaimRepository;
import com.nanbei.entertainment.backend.mission.infrastructure.MissionMilestoneDefinitionRepository;
import com.nanbei.entertainment.backend.mission.infrastructure.MissionRewardRepository;
import com.nanbei.entertainment.backend.mission.infrastructure.MissionTaskDefinitionRepository;
import com.nanbei.entertainment.backend.mission.infrastructure.UserMissionProgressRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class MissionClaimService {
    private static final String TASK = "TASK";
    private static final String MILESTONE = "MILESTONE";

    private final MissionTaskDefinitionRepository taskRepository;
    private final MissionMilestoneDefinitionRepository milestoneRepository;
    private final UserMissionProgressRepository progressRepository;
    private final MissionMilestoneClaimRepository milestoneClaimRepository;
    private final MissionClaimRequestRepository claimRequestRepository;
    private final MissionRewardRepository rewardRepository;
    private final MissionRewardService rewardService;
    private final MissionQueryService queryService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MissionClaimService(
            MissionTaskDefinitionRepository taskRepository,
            MissionMilestoneDefinitionRepository milestoneRepository,
            UserMissionProgressRepository progressRepository,
            MissionMilestoneClaimRepository milestoneClaimRepository,
            MissionClaimRequestRepository claimRequestRepository,
            MissionRewardRepository rewardRepository,
            MissionRewardService rewardService,
            MissionQueryService queryService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.milestoneRepository = milestoneRepository;
        this.progressRepository = progressRepository;
        this.milestoneClaimRepository = milestoneClaimRepository;
        this.claimRequestRepository = claimRequestRepository;
        this.rewardRepository = rewardRepository;
        this.rewardService = rewardService;
        this.queryService = queryService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public MissionPageStatus claimTask(UUID userId, String requestedTaskCode, String key) {
        String taskCode = normalize(requestedTaskCode);
        acquireClaimLock(userId, key);
        MissionPageStatus replay = replay(userId, key, TASK, taskCode);
        if (replay != null) return replay;
        MissionClaimRequestEntity request = startRequest(userId, key, TASK, taskCode);
        MissionTaskDefinitionEntity task = taskRepository
                .findByTaskCodeAndEnabledTrue(taskCode)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.MISSION_TASK_NOT_FOUND, "任务不存在"));
        MissionPageDefinitionEntity page = queryService.requirePage(task.getPageCode());
        requireClaimWindow(task);
        Instant cycleStart = MissionCycle.start(page.getCycleType(), clock.instant());
        UserMissionProgressEntity progress = progressRepository
                .findLocked(userId, taskCode, cycleStart)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.MISSION_TASK_NOT_CLAIMABLE, "任务尚未完成"));
        if (progress.isClaimed()) {
            throw new ApiException(ErrorCode.MISSION_TASK_ALREADY_CLAIMED, "任务奖励已领取");
        }
        if (!progress.isComplete()) {
            throw new ApiException(ErrorCode.MISSION_TASK_NOT_CLAIMABLE, "任务尚未完成");
        }
        progress.claim(clock.instant());
        progressRepository.save(progress);
        rewardService.grant(
                userId, rewardRepository.findByTaskCodeOrderByRewardOrderAsc(taskCode));
        return complete(request, queryService.buildPage(userId, page));
    }

    /**
     * 限时任务只能在自己的窗口内领奖；活动结束后允许在 drawDeadline 之前继续领取，
     * 与 View.lua 保留待领取任务到 drawDeadline 的表现一致。窗口列为空时跟随页签周期，
     * 周期本身已由 cycleStart 锁定进度行，不需要额外校验。
     */
    private void requireClaimWindow(MissionTaskDefinitionEntity task) {
        Instant now = clock.instant();
        if (task.getStartsAt() != null && now.isBefore(task.getStartsAt())) {
            throw new ApiException(ErrorCode.MISSION_TASK_NOT_CLAIMABLE, "任务尚未开始");
        }
        Instant endsAt = task.getEndsAt();
        if (endsAt == null || !now.isAfter(endsAt)) return;
        Instant drawDeadline = task.getDrawDeadline();
        if (drawDeadline == null || now.isAfter(drawDeadline)) {
            throw new ApiException(ErrorCode.MISSION_TASK_NOT_CLAIMABLE, "任务领取已截止");
        }
    }

    @Transactional
    public MissionPageStatus claimMilestone(
            UUID userId, String requestedPageCode, long target, String key) {
        String pageCode = normalize(requestedPageCode);
        String reference = pageCode + ":" + target;
        acquireClaimLock(userId, key);
        MissionPageStatus replay = replay(userId, key, MILESTONE, reference);
        if (replay != null) return replay;
        MissionClaimRequestEntity request = startRequest(userId, key, MILESTONE, reference);
        MissionPageDefinitionEntity page = queryService.requirePage(pageCode);
        MissionMilestoneDefinitionEntity milestone = milestoneRepository
                .findByPageCodeAndTargetAndEnabledTrue(pageCode, target)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.MISSION_MILESTONE_NOT_CLAIMABLE, "阶段奖励不存在"));
        Instant cycleStart = MissionCycle.start(page.getCycleType(), clock.instant());
        if (milestoneClaimRepository
                .findByUserIdAndPageCodeAndCycleStartedAtAndTarget(
                        userId, pageCode, cycleStart, target)
                .isPresent()) {
            throw new ApiException(
                    ErrorCode.MISSION_MILESTONE_ALREADY_CLAIMED, "阶段奖励已领取");
        }
        MissionPageStatus before = queryService.buildPage(userId, page);
        if (before.activityPoints() < target) {
            throw new ApiException(
                    ErrorCode.MISSION_MILESTONE_NOT_CLAIMABLE, "累计活跃值不足");
        }
        milestoneClaimRepository.save(new MissionMilestoneClaimEntity(
                userId, pageCode, cycleStart, target, clock.instant()));
        rewardService.grant(userId, rewardRepository
                .findByPageCodeAndMilestoneTargetOrderByRewardOrderAsc(
                        pageCode, milestone.getTarget()));
        return complete(request, queryService.buildPage(userId, page));
    }

    private MissionPageStatus replay(
            UUID userId, String key, String type, String reference) {
        return claimRequestRepository.findLockedByKey(userId, key)
                .map(request -> replayExisting(request, type, reference))
                .orElse(null);
    }

    private void acquireClaimLock(UUID userId, String key) {
        claimRequestRepository.acquireIdempotencyLock(
                "mission-claim:" + userId + ":" + key);
    }

    private MissionPageStatus replayExisting(
            MissionClaimRequestEntity request, String type, String reference) {
        if (!request.matches(type, reference)) {
            throw new ApiException(
                    ErrorCode.MISSION_IDEMPOTENCY_CONFLICT,
                    "Idempotency-Key 已用于其他领取请求");
        }
        if (request.getResponsePayload() == null) {
            throw new ApiException(
                    ErrorCode.MISSION_IDEMPOTENCY_CONFLICT, "领取请求仍在处理中");
        }
        try {
            return objectMapper.readValue(
                    request.getResponsePayload(), MissionPageStatus.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read mission claim response", exception);
        }
    }

    private MissionClaimRequestEntity startRequest(
            UUID userId, String key, String type, String reference) {
        MissionClaimRequestEntity request = new MissionClaimRequestEntity(
                userId, key, type, reference, clock.instant());
        return claimRequestRepository.save(request);
    }

    private MissionPageStatus complete(
            MissionClaimRequestEntity request, MissionPageStatus response) {
        try {
            request.complete(objectMapper.writeValueAsString(response), clock.instant());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize mission claim response", exception);
        }
        claimRequestRepository.save(request);
        return response;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
