package com.nanbei.entertainment.backend.mission.application;

import static com.nanbei.entertainment.backend.mission.application.MissionResponses.MissionTaskState.CLAIMABLE;
import static com.nanbei.entertainment.backend.mission.application.MissionResponses.MissionTaskState.CLAIMED;
import static com.nanbei.entertainment.backend.mission.application.MissionResponses.MissionTaskState.IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.mission.domain.MissionCycleType;
import com.nanbei.entertainment.backend.mission.domain.MissionPageDefinitionEntity;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MissionQueryServiceTest {
    @Mock MissionPageDefinitionRepository pages;
    @Mock MissionTaskDefinitionRepository tasks;
    @Mock MissionMilestoneDefinitionRepository milestones;
    @Mock MissionRewardRepository rewards;
    @Mock UserMissionProgressRepository progress;
    @Mock MissionMilestoneClaimRepository milestoneClaims;
    @Mock PlayerWalletRepository wallets;
    @Mock MissionProgressService progressService;

    MissionQueryService service;
    UUID userId;
    Instant cycleStart;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-05T12:00:00Z"), ZoneOffset.UTC);
        service =
                new MissionQueryService(
                        pages,
                        tasks,
                        milestones,
                        rewards,
                        progress,
                        milestoneClaims,
                        wallets,
                        progressService,
                        clock);
        userId = UUID.randomUUID();
        cycleStart = Instant.parse("2026-08-04T20:00:00Z");
    }

    @Test
    void dailyLoginBecomesClaimableAndCatalogExposesRedPoint() {
        MissionPageDefinitionEntity daily = page("DAILY", "每日任务", MissionCycleType.DAILY, 10);
        MissionTaskDefinitionEntity login = task("DAILY_LOGIN", "每日登录奖励", 1, 400, 10);
        UserMissionProgressEntity loginProgress =
                new UserMissionProgressEntity(userId, "DAILY_LOGIN", cycleStart, 1);
        loginProgress.increment(1);
        when(pages.findByPageCodeAndEnabledTrue("DAILY")).thenReturn(Optional.of(daily));
        when(pages.findByEnabledTrueOrderByDisplayOrderAsc()).thenReturn(List.of(daily));
        when(tasks.findByPageCodeAndEnabledTrueOrderByDisplayOrderAsc("DAILY"))
                .thenReturn(List.of(login));
        when(progress.findByUserIdAndTaskCodeAndCycleStartedAt(userId, "DAILY_LOGIN", cycleStart))
                .thenReturn(Optional.of(loginProgress));
        when(milestones.findByPageCodeAndEnabledTrueOrderByDisplayOrderAsc("DAILY"))
                .thenReturn(List.of());
        when(milestoneClaims.findByUserIdAndPageCodeAndCycleStartedAt(userId, "DAILY", cycleStart))
                .thenReturn(List.of());
        when(rewards.findByTaskCodeOrderByRewardOrderAsc("DAILY_LOGIN")).thenReturn(List.of());
        when(wallets.findById(userId)).thenReturn(Optional.empty());

        MissionResponses.MissionPageStatus page = service.page(userId, "DAILY");
        MissionResponses.MissionCatalogResponse catalog = service.catalog(userId);

        assertThat(page.tasks().getFirst().taskCode()).isEqualTo("DAILY_LOGIN");
        assertThat(page.tasks().getFirst().state()).isEqualTo(CLAIMABLE);
        assertThat(catalog.pages().getFirst().redPoint()).isTrue();
    }

    @Test
    void tasksSortClaimableThenInProgressThenClaimed() {
        MissionPageDefinitionEntity daily = page("DAILY", "每日任务", MissionCycleType.DAILY, 10);
        MissionTaskDefinitionEntity claimed = task("CLAIMED", "已领取", 1, 100, 10);
        MissionTaskDefinitionEntity inProgress = task("PROGRESS", "进行中", 2, 100, 20);
        MissionTaskDefinitionEntity claimable = task("CLAIMABLE", "可领取", 1, 100, 30);
        UserMissionProgressEntity claimedProgress = progress("CLAIMED", 1, true);
        UserMissionProgressEntity partialProgress = progress("PROGRESS", 1, false);
        UserMissionProgressEntity completeProgress = progress("CLAIMABLE", 1, false);
        when(pages.findByPageCodeAndEnabledTrue("DAILY")).thenReturn(Optional.of(daily));
        when(tasks.findByPageCodeAndEnabledTrueOrderByDisplayOrderAsc("DAILY"))
                .thenReturn(List.of(claimed, inProgress, claimable));
        when(progress.findByUserIdAndTaskCodeAndCycleStartedAt(userId, "CLAIMED", cycleStart))
                .thenReturn(Optional.of(claimedProgress));
        when(progress.findByUserIdAndTaskCodeAndCycleStartedAt(userId, "PROGRESS", cycleStart))
                .thenReturn(Optional.of(partialProgress));
        when(progress.findByUserIdAndTaskCodeAndCycleStartedAt(userId, "CLAIMABLE", cycleStart))
                .thenReturn(Optional.of(completeProgress));
        when(milestones.findByPageCodeAndEnabledTrueOrderByDisplayOrderAsc("DAILY"))
                .thenReturn(List.of());
        when(milestoneClaims.findByUserIdAndPageCodeAndCycleStartedAt(userId, "DAILY", cycleStart))
                .thenReturn(List.of());
        when(rewards.findByTaskCodeOrderByRewardOrderAsc(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of());
        when(wallets.findById(userId)).thenReturn(Optional.empty());

        assertThat(service.page(userId, "DAILY").tasks())
                .extracting(MissionResponses.MissionTaskStatus::state)
                .containsExactly(CLAIMABLE, IN_PROGRESS, CLAIMED);
    }

    @Test
    void tasksWithoutOwnWindowFallBackToThePageCycle() {
        MissionPageDefinitionEntity daily = page("DAILY", "每日任务", MissionCycleType.DAILY, 10);
        MissionTaskDefinitionEntity login = task("DAILY_LOGIN", "每日登录奖励", 1, 400, 10);
        when(pages.findByPageCodeAndEnabledTrue("DAILY")).thenReturn(Optional.of(daily));
        when(tasks.findByPageCodeAndEnabledTrueOrderByDisplayOrderAsc("DAILY"))
                .thenReturn(List.of(login));
        when(progress.findByUserIdAndTaskCodeAndCycleStartedAt(userId, "DAILY_LOGIN", cycleStart))
                .thenReturn(Optional.empty());
        when(milestones.findByPageCodeAndEnabledTrueOrderByDisplayOrderAsc("DAILY"))
                .thenReturn(List.of());
        when(milestoneClaims.findByUserIdAndPageCodeAndCycleStartedAt(userId, "DAILY", cycleStart))
                .thenReturn(List.of());
        when(rewards.findByTaskCodeOrderByRewardOrderAsc("DAILY_LOGIN")).thenReturn(List.of());
        when(wallets.findById(userId)).thenReturn(Optional.empty());

        MissionResponses.MissionPageStatus page = service.page(userId, "DAILY");

        // 与页签 endsAt 相同表示不是限时任务，客户端不画 KW_LEFT_TIME 角标。
        assertThat(page.tasks().getFirst().startsAt()).isEqualTo(cycleStart);
        assertThat(page.tasks().getFirst().endsAt()).isEqualTo(page.page().expiresAt());
        assertThat(page.tasks().getFirst().drawDeadline()).isNull();
    }

    @Test
    void expiredLimitedTaskStaysVisibleOnlyWhileClaimableInsideItsDrawDeadline() {
        Instant now = Instant.parse("2026-08-05T12:00:00Z");
        Instant ended = Instant.parse("2026-08-05T11:00:00Z");
        Instant running = Instant.parse("2026-08-05T13:00:00Z");

        assertThat(MissionQueryService.visible(limited(running, null, IN_PROGRESS), now)).isTrue();
        assertThat(MissionQueryService.visible(limited(ended, null, CLAIMABLE), now)).isFalse();
        assertThat(MissionQueryService.visible(limited(ended, null, IN_PROGRESS), now)).isFalse();
        assertThat(
                        MissionQueryService.visible(
                                limited(ended, Instant.parse("2026-08-05T18:00:00Z"), CLAIMABLE),
                                now))
                .isTrue();
        assertThat(
                        MissionQueryService.visible(
                                limited(ended, Instant.parse("2026-08-05T18:00:00Z"), IN_PROGRESS),
                                now))
                .isFalse();
        assertThat(
                        MissionQueryService.visible(
                                limited(ended, Instant.parse("2026-08-05T11:30:00Z"), CLAIMABLE),
                                now))
                .isFalse();
    }

    private static MissionResponses.MissionTaskStatus limited(
            Instant endsAt,
            Instant drawDeadline,
            MissionResponses.MissionTaskState state) {
        return new MissionResponses.MissionTaskStatus(
                "LIMITED",
                "限时任务",
                0,
                1,
                100,
                state,
                "GAME_HOME",
                10,
                Instant.parse("2026-08-05T00:00:00Z"),
                endsAt,
                drawDeadline,
                List.of());
    }

    private UserMissionProgressEntity progress(String code, int value, boolean claimed) {
        long target = code.equals("PROGRESS") ? 2 : 1;
        UserMissionProgressEntity entity =
                new UserMissionProgressEntity(userId, code, cycleStart, target);
        entity.increment(value);
        if (claimed) entity.claim(Instant.parse("2026-08-05T10:00:00Z"));
        return entity;
    }

    private static MissionPageDefinitionEntity page(
            String code, String name, MissionCycleType cycleType, int order) {
        MissionPageDefinitionEntity entity = mock(MissionPageDefinitionEntity.class);
        when(entity.getPageCode()).thenReturn(code);
        when(entity.getDisplayName()).thenReturn(name);
        when(entity.getCycleType()).thenReturn(cycleType);
        return entity;
    }

    private static MissionTaskDefinitionEntity task(
            String code, String name, long target, long activity, int order) {
        MissionTaskDefinitionEntity entity = mock(MissionTaskDefinitionEntity.class);
        when(entity.getTaskCode()).thenReturn(code);
        when(entity.getDisplayName()).thenReturn(name);
        when(entity.getTarget()).thenReturn(target);
        when(entity.getActivityPoints()).thenReturn(activity);
        when(entity.getJumpType()).thenReturn("GAME_HOME");
        when(entity.getDisplayOrder()).thenReturn(order);
        return entity;
    }
}
