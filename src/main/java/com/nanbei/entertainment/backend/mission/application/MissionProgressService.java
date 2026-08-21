package com.nanbei.entertainment.backend.mission.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.mission.domain.MissionCycle;
import com.nanbei.entertainment.backend.mission.domain.MissionEventType;
import com.nanbei.entertainment.backend.mission.domain.MissionPageDefinitionEntity;
import com.nanbei.entertainment.backend.mission.domain.MissionTaskDefinitionEntity;
import com.nanbei.entertainment.backend.mission.domain.UserMissionProgressEntity;
import com.nanbei.entertainment.backend.mission.infrastructure.MissionPageDefinitionRepository;
import com.nanbei.entertainment.backend.mission.infrastructure.MissionTaskDefinitionRepository;
import com.nanbei.entertainment.backend.mission.infrastructure.UserMissionProgressRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MissionProgressService {
    private final MissionTaskDefinitionRepository taskRepository;
    private final MissionPageDefinitionRepository pageRepository;
    private final UserMissionProgressRepository progressRepository;
    private final Clock clock;

    public MissionProgressService(
            MissionTaskDefinitionRepository taskRepository,
            MissionPageDefinitionRepository pageRepository,
            UserMissionProgressRepository progressRepository,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.pageRepository = pageRepository;
        this.progressRepository = progressRepository;
        this.clock = clock;
    }

    @Transactional
    public void record(UUID userId, MissionEventType eventType, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("mission event amount must be positive");
        Instant now = clock.instant();
        for (MissionTaskDefinitionEntity task : taskRepository.findByEventTypeAndEnabledTrue(eventType)) {
            MissionPageDefinitionEntity page =
                    pageRepository.findByPageCodeAndEnabledTrue(task.getPageCode())
                            .orElseThrow(() -> new ApiException(
                                    ErrorCode.MISSION_PAGE_NOT_FOUND, "任务页面不存在"));
            Instant cycleStart = MissionCycle.start(page.getCycleType(), now);
            UserMissionProgressEntity progress =
                    progressRepository.findLocked(userId, task.getTaskCode(), cycleStart)
                            .orElseGet(() -> progressRepository.save(
                                    new UserMissionProgressEntity(
                                            userId, task.getTaskCode(), cycleStart, task.getTarget())));
            progress.increment(amount);
            progressRepository.save(progress);
        }
    }
}
