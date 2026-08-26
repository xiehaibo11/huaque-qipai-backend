package com.nanbei.entertainment.backend.gamerecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.gamerecord.application.GameRecordMode;
import com.nanbei.entertainment.backend.gamerecord.application.GameRecordPage;
import com.nanbei.entertainment.backend.gamerecord.application.GameRecordService;
import com.nanbei.entertainment.backend.gamerecord.infrastructure.GameRecordRepository;
import com.nanbei.entertainment.backend.membership.application.MembershipStatusService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GameRecordServiceTest {
    private static final UUID VIEWER =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID SESSION =
            UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final LocalDate DATE = LocalDate.of(2026, 8, 24);

    @Mock MembershipStatusService membershipStatusService;
    @Mock GameRecordRepository repository;

    GameRecordService service;

    @BeforeEach
    void setUp() {
        service =
                new GameRecordService(
                        membershipStatusService,
                        repository,
                        Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void groupsRealSessionRowsAndComputesTheOriginalDailySummary() {
        when(membershipStatusService.isActive(VIEWER)).thenReturn(true);
        when(repository.find(VIEWER, DATE, 0L, false))
                .thenReturn(
                        List.of(
                                row(1, VIEWER, 1084375590L, "WhimSeeker", 12L, true),
                                row(2, OTHER, 1084375591L, "牌友", -12L, false)));

        GameRecordPage page = service.page(VIEWER, DATE, 0L, GameRecordMode.BATTLE);

        assertThat(page.date()).isEqualTo(DATE);
        assertThat(page.membershipActive()).isTrue();
        assertThat(page.gameIds()).containsExactly(30109L);
        assertThat(page.summary().championCount()).isEqualTo(1);
        assertThat(page.summary().score()).isEqualTo(12L);
        assertThat(page.summary().roundCount()).isEqualTo(1);
        assertThat(page.records()).hasSize(1);
        assertThat(page.records().getFirst().players()).hasSize(2);
        assertThat(page.records().getFirst().players().getFirst().self()).isTrue();
    }

    @Test
    void nonMemberGoldRecordsStayLockedAndNeverQueryPrivateHistory() {
        when(membershipStatusService.isActive(VIEWER)).thenReturn(false);

        GameRecordPage page = service.page(VIEWER, DATE, 0L, GameRecordMode.GOLD);

        assertThat(page.membershipActive()).isFalse();
        assertThat(page.records()).isEmpty();
        verify(repository, never()).find(VIEWER, DATE, 0L, true);
    }

    private static GameRecordRepository.Row row(
            int seat,
            UUID userId,
            long publicPlayerId,
            String displayName,
            long score,
            boolean host) {
        return new GameRecordRepository.Row(
                SESSION,
                "123456",
                30109L,
                8,
                8,
                Instant.parse("2026-08-24T12:30:00Z"),
                seat,
                userId,
                publicPlayerId,
                displayName,
                score,
                host);
    }
}
