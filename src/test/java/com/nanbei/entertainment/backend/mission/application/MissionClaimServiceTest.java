package com.nanbei.entertainment.backend.mission.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.mission.application.MissionResponses.MissionPageStatus;
import com.nanbei.entertainment.backend.mission.application.MissionResponses.MissionPageSummary;
import com.nanbei.entertainment.backend.mission.application.MissionResponses.MissionWalletSnapshot;
import com.nanbei.entertainment.backend.mission.domain.MissionClaimRequestEntity;
import com.nanbei.entertainment.backend.mission.domain.MissionCycleType;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class MissionClaimServiceTest {
    @Mock MissionTaskDefinitionRepository tasks;
    @Mock MissionMilestoneDefinitionRepository milestones;
    @Mock UserMissionProgressRepository progress;
    @Mock MissionMilestoneClaimRepository milestoneClaims;
    @Mock MissionClaimRequestRepository claimRequests;
    @Mock MissionRewardRepository rewards;
    @Mock MissionRewardService rewardService;
    @Mock MissionQueryService queryService;
    @Mock ObjectMapper objectMapper;

    MissionClaimService service;
    UUID userId;
    Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-08-05T12:00:00Z");
        service = new MissionClaimService(
                tasks,
                milestones,
                progress,
                milestoneClaims,
                claimRequests,
                rewards,
                rewardService,
                queryService,
                objectMapper,
                Clock.fixed(now, ZoneOffset.UTC));
        userId = UUID.randomUUID();
    }

    @Test
    void retryWithSameIdempotencyKeyDoesNotGrantTwice() throws Exception {
        MissionTaskDefinitionEntity task = mock(MissionTaskDefinitionEntity.class);
        MissionPageDefinitionEntity page = mock(MissionPageDefinitionEntity.class);
        when(task.getPageCode()).thenReturn("DAILY");
        when(page.getCycleType()).thenReturn(MissionCycleType.DAILY);
        when(tasks.findByTaskCodeAndEnabledTrue("DAILY_LOGIN")).thenReturn(Optional.of(task));
        when(queryService.requirePage("DAILY")).thenReturn(page);
        UserMissionProgressEntity completed = new UserMissionProgressEntity(
                userId, "DAILY_LOGIN", Instant.parse("2026-08-04T20:00:00Z"), 1);
        completed.increment(1);
        when(progress.findLocked(
                        userId, "DAILY_LOGIN", Instant.parse("2026-08-04T20:00:00Z")))
                .thenReturn(Optional.of(completed));
        when(rewards.findByTaskCodeOrderByRewardOrderAsc("DAILY_LOGIN"))
                .thenReturn(List.of());
        MissionPageStatus response = response();
        when(queryService.buildPage(userId, page)).thenReturn(response);
        when(objectMapper.writeValueAsString(response)).thenReturn("{\"page\":\"DAILY\"}");
        when(objectMapper.readValue("{\"page\":\"DAILY\"}", MissionPageStatus.class))
                .thenReturn(response);
        AtomicReference<MissionClaimRequestEntity> stored = new AtomicReference<>();
        when(claimRequests.findLockedByKey(userId, "claim-key-1"))
                .thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(claimRequests.save(any())).thenAnswer(call -> {
            MissionClaimRequestEntity request = call.getArgument(0);
            stored.set(request);
            return request;
        });

        assertThat(service.claimTask(userId, "DAILY_LOGIN", "claim-key-1"))
                .isEqualTo(response);
        assertThat(service.claimTask(userId, "DAILY_LOGIN", "claim-key-1"))
                .isEqualTo(response);

        assertThat(completed.isClaimed()).isTrue();
        verify(rewardService, times(1)).grant(userId, List.of());
    }

    @Test
    void sameKeyForDifferentClaimIsRejected() {
        MissionClaimRequestEntity request = new MissionClaimRequestEntity(
                userId, "same-key", "TASK", "DAILY_LOGIN", now);
        when(claimRequests.findLockedByKey(userId, "same-key"))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.claimMilestone(userId, "DAILY", 800, "same-key"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code())
                                .isEqualTo(ErrorCode.MISSION_IDEMPOTENCY_CONFLICT));
    }

    private MissionPageStatus response() {
        MissionPageSummary summary = new MissionPageSummary(
                "DAILY", "每日任务", MissionCycleType.DAILY,
                Instant.parse("2026-08-05T20:00:00Z"), false);
        return new MissionPageStatus(
                now,
                summary,
                List.of(summary),
                400,
                List.of(),
                List.of(),
                new MissionWalletSnapshot(0, 300, 0, 0));
    }
}
