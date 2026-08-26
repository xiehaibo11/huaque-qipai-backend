package com.nanbei.entertainment.backend.scoreassistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerEntity;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerStatus;
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
class ScoreLedgerLifecycleServiceTest {
    private static final UUID OWNER =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
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
    }

    @Test
    void endsAnInProgressLedgerAndPreventsFurtherRounds() {
        ScoreLedgerEntity ledger = ledger();
        ownLockedLedger(ledger);

        ScoreLedgerStateResponse ended = service.end(OWNER, ledger.getId());

        assertThat(ended.status()).isEqualTo(ScoreLedgerStatus.ENDED);
        assertThat(ended.endedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> service.recordRound(OWNER, ledger.getId(), List.of()))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.SCORE_LEDGER_ILLEGAL_STATE);
        assertThatThrownBy(() -> service.end(OWNER, ledger.getId()))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.SCORE_LEDGER_ILLEGAL_STATE);
    }

    @Test
    void setsAndClearsFavoriteOnlyOnAnOwnedLedger() {
        ScoreLedgerEntity ledger = ledger();
        ownLockedLedger(ledger);

        assertThat(service.setFavorite(OWNER, ledger.getId(), true).favorite()).isTrue();
        assertThat(service.setFavorite(OWNER, ledger.getId(), false).favorite()).isFalse();
    }

    @Test
    void softDeletesAnOwnedLedger() {
        ScoreLedgerEntity ledger = ledger();
        ownLockedLedger(ledger);

        ScoreLedgerDeleteResponse deleted = service.delete(OWNER, ledger.getId());

        assertThat(deleted.ledgerId()).isEqualTo(ledger.getId());
        assertThat(deleted.deletedAt()).isEqualTo(NOW);
        assertThat(ledger.getDeletedAt()).isEqualTo(NOW);
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
