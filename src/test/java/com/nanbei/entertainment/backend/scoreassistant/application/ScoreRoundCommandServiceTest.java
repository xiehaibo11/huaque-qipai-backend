package com.nanbei.entertainment.backend.scoreassistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerEntity;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerPlayerEntity;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerRoundEntity;
import com.nanbei.entertainment.backend.scoreassistant.infrastructure.ScoreLedgerRepository;
import com.nanbei.entertainment.backend.scoreassistant.infrastructure.ScoreLedgerRoundRepository;
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
class ScoreRoundCommandServiceTest {
    private static final UUID OWNER =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID STRANGER =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");

    @Mock ScoreLedgerRepository ledgerRepository;
    @Mock ScoreLedgerRoundRepository roundRepository;

    ScoreLedgerCommandService service;

    @BeforeEach
    void setUp() {
        service = new ScoreLedgerCommandService(
                ledgerRepository,
                roundRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(roundRepository.save(any(ScoreLedgerRoundEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void recordsAZeroSumRoundAndComputesAuthoritativeTotals() {
        ScoreLedgerEntity ledger = ledger();
        ownLockedLedger(ledger);
        ScoreLedgerPlayerEntity self = ledger.getPlayers().get(0);
        ScoreLedgerPlayerEntity friend = ledger.getPlayers().get(1);

        ScoreRoundResponse result = service.recordRound(
                OWNER,
                ledger.getId(),
                List.of(
                        new RecordScore(self.getId(), 18),
                        new RecordScore(friend.getId(), -18)));

        assertThat(result.roundNumber()).isEqualTo(1);
        assertThat(result.recordedAt()).isEqualTo(NOW);
        assertThat(result.scores())
                .extracting(ScoreRoundResponse.Score::scoreDelta)
                .containsExactly(18L, -18L);
        assertThat(result.scores())
                .extracting(ScoreRoundResponse.Score::totalAfter)
                .containsExactly(18L, -18L);
        assertThat(self.getTotalScore()).isEqualTo(18);
        assertThat(friend.getTotalScore()).isEqualTo(-18);
        assertThat(ledger.getRoundCount()).isEqualTo(1);
    }

    @Test
    void aLaterRoundAccumulatesOnlyFromServerState() {
        ScoreLedgerEntity ledger = ledger();
        ownLockedLedger(ledger);
        ScoreLedgerPlayerEntity self = ledger.getPlayers().get(0);
        ScoreLedgerPlayerEntity friend = ledger.getPlayers().get(1);
        service.recordRound(
                OWNER,
                ledger.getId(),
                List.of(
                        new RecordScore(self.getId(), 18),
                        new RecordScore(friend.getId(), -18)));

        ScoreRoundResponse second = service.recordRound(
                OWNER,
                ledger.getId(),
                List.of(
                        new RecordScore(self.getId(), -5),
                        new RecordScore(friend.getId(), 5)));

        assertThat(second.roundNumber()).isEqualTo(2);
        assertThat(second.scores())
                .extracting(ScoreRoundResponse.Score::totalAfter)
                .containsExactly(13L, -13L);
        assertThat(ledger.getRoundCount()).isEqualTo(2);
    }

    @Test
    void rejectsNonZeroMissingDuplicateAndForeignPlayerScoresWithoutMutation() {
        ScoreLedgerEntity ledger = ledger();
        ownLockedLedger(ledger);
        ScoreLedgerPlayerEntity self = ledger.getPlayers().get(0);
        ScoreLedgerPlayerEntity friend = ledger.getPlayers().get(1);

        assertInvalid(ledger, List.of(
                new RecordScore(self.getId(), 10),
                new RecordScore(friend.getId(), -9)));
        assertInvalid(ledger, List.of(new RecordScore(self.getId(), 0)));
        assertInvalid(ledger, List.of(
                new RecordScore(self.getId(), 0),
                new RecordScore(self.getId(), 0)));
        assertInvalid(ledger, List.of(
                new RecordScore(self.getId(), 0),
                new RecordScore(UUID.randomUUID(), 0)));

        assertThat(self.getTotalScore()).isZero();
        assertThat(friend.getTotalScore()).isZero();
        assertThat(ledger.getRoundCount()).isZero();
    }

    @Test
    void aForeignOwnerCannotDiscoverOrMutateTheLedger() {
        UUID ledgerId = UUID.randomUUID();
        when(ledgerRepository.findOwnedForUpdate(ledgerId, STRANGER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordRound(STRANGER, ledgerId, List.of()))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.SCORE_LEDGER_NOT_FOUND);
    }

    private void assertInvalid(ScoreLedgerEntity ledger, List<RecordScore> scores) {
        assertThatThrownBy(() -> service.recordRound(OWNER, ledger.getId(), scores))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.SCORE_LEDGER_INVALID);
    }

    private void ownLockedLedger(ScoreLedgerEntity ledger) {
        when(ledgerRepository.findOwnedForUpdate(ledger.getId(), OWNER))
                .thenReturn(Optional.of(ledger));
    }

    private static ScoreLedgerEntity ledger() {
        return new ScoreLedgerEntity(
                OWNER,
                List.of(
                        new ScoreLedgerEntity.NamedPlayer("WhimSeeker", true),
                        new ScoreLedgerEntity.NamedPlayer("牌友", false)),
                NOW.minusSeconds(60));
    }
}
