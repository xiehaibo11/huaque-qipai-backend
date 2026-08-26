package com.nanbei.entertainment.backend.scoreassistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerEntity;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerPlayerEntity;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerRoundEntity;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerStatus;
import com.nanbei.entertainment.backend.scoreassistant.infrastructure.ScoreLedgerRepository;
import com.nanbei.entertainment.backend.scoreassistant.infrastructure.ScoreLedgerRoundRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ScoreLedgerQueryServiceTest {
    private static final UUID OWNER =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID STRANGER =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");

    @Mock ScoreLedgerRepository ledgerRepository;
    @Mock ScoreLedgerRoundRepository roundRepository;

    ScoreLedgerQueryService service;

    @BeforeEach
    void setUp() {
        service = new ScoreLedgerQueryService(
                ledgerRepository,
                roundRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void returnsOwnedInProgressLedgersAndTheirAuthoritativeTotals() {
        ScoreLedgerEntity ledger = ledger("WhimSeeker", "牌友");
        totals(ledger, 18, -18);
        when(ledgerRepository
                        .findByOwnerUserIdAndStatusAndDeletedAtIsNullOrderByStartedAtDesc(
                                OWNER, ScoreLedgerStatus.IN_PROGRESS))
                .thenReturn(List.of(ledger));

        ScoreLedgerListResponse response = service.inProgress(OWNER);

        assertThat(response.ledgers()).hasSize(1);
        assertThat(response.ledgers().getFirst().players())
                .extracting(ScoreLedgerSummary.Player::totalScore)
                .containsExactly(18L, -18L);
    }

    @Test
    void returnsOwnedDetailWithAllRoundsButHidesForeignLedgers() {
        ScoreLedgerEntity ledger = ledger("WhimSeeker", "牌友");
        ScoreLedgerRoundEntity round = round(ledger, 1, 8, -8);
        when(ledgerRepository.findOwned(ledger.getId(), OWNER))
                .thenReturn(Optional.of(ledger));
        when(roundRepository.findDetailedByLedgerId(ledger.getId()))
                .thenReturn(List.of(round));

        ScoreLedgerDetailResponse detail = service.detail(OWNER, ledger.getId());

        assertThat(detail.rounds()).hasSize(1);
        assertThat(detail.rounds().getFirst().scores())
                .extracting(ScoreLedgerDetailResponse.Score::scoreDelta)
                .containsExactly(8L, -8L);

        when(ledgerRepository.findOwned(ledger.getId(), STRANGER))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail(STRANGER, ledger.getId()))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.SCORE_LEDGER_NOT_FOUND);
    }

    @Test
    void returnsOneBasedPagedEndedHistory() {
        ScoreLedgerEntity ended = ledger("WhimSeeker", "牌友");
        ended.end(NOW.minusSeconds(30));
        when(ledgerRepository.findHistory(
                        eq(OWNER),
                        eq(ScoreLedgerStatus.ENDED),
                        any(PageRequest.class)))
                .thenReturn(new PageImpl<>(
                        List.of(ended, ended, ended), PageRequest.of(1, 10), 13));

        ScoreLedgerHistoryPage page = service.history(OWNER, 2, 10);

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.pageSize()).isEqualTo(10);
        assertThat(page.totalCount()).isEqualTo(13);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.ledgers()).hasSize(3);
    }

    @Test
    void computesMonthlyLedgerStatisticsAndCompanionExtremesFromServerTotals() {
        ScoreLedgerEntity win = ledger("WhimSeeker", "牌友甲");
        totals(win, 30, -30);
        win.end(Instant.parse("2026-08-03T02:00:00Z"));
        ScoreLedgerEntity loss = ledger("WhimSeeker", "牌友乙");
        totals(loss, -10, 10);
        loss.end(Instant.parse("2026-08-20T02:00:00Z"));
        when(ledgerRepository.findEndedInPeriod(eq(OWNER), any(), any()))
                .thenReturn(List.of(win, loss));

        ScoreLedgerMonthlyStatistics statistics =
                service.monthly(OWNER, YearMonth.of(2026, 8));

        assertThat(statistics.month()).isEqualTo(YearMonth.of(2026, 8));
        assertThat(statistics.totalPlay()).isEqualTo(2);
        assertThat(statistics.winPlay()).isEqualTo(1);
        assertThat(statistics.lossPlay()).isEqualTo(1);
        assertThat(statistics.totalScore()).isEqualTo(20);
        assertThat(statistics.winScore()).isEqualTo(30);
        assertThat(statistics.lossScore()).isEqualTo(-10);
        assertThat(statistics.winMax()).isEqualTo("牌友乙");
        assertThat(statistics.lostMax()).isEqualTo("牌友甲");
    }

    private static ScoreLedgerEntity ledger(String self, String companion) {
        return new ScoreLedgerEntity(
                OWNER,
                List.of(
                        new ScoreLedgerEntity.NamedPlayer(self, true),
                        new ScoreLedgerEntity.NamedPlayer(companion, false)),
                NOW.minusSeconds(120));
    }

    private static void totals(ScoreLedgerEntity ledger, long first, long second) {
        ledger.getPlayers().get(0).setTotalScore(first);
        ledger.getPlayers().get(1).setTotalScore(second);
    }

    private static ScoreLedgerRoundEntity round(
            ScoreLedgerEntity ledger, int number, long first, long second) {
        List<ScoreLedgerPlayerEntity> players = ledger.getPlayers();
        Map<ScoreLedgerPlayerEntity, Long> deltas = new LinkedHashMap<>();
        deltas.put(players.get(0), first);
        deltas.put(players.get(1), second);
        Map<ScoreLedgerPlayerEntity, Long> totals = new LinkedHashMap<>(deltas);
        return new ScoreLedgerRoundEntity(ledger, number, NOW, deltas, totals);
    }
}
