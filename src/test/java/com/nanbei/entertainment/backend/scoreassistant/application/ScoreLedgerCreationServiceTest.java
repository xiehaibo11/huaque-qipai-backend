package com.nanbei.entertainment.backend.scoreassistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerEntity;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerStatus;
import com.nanbei.entertainment.backend.scoreassistant.infrastructure.ScoreLedgerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScoreLedgerCreationServiceTest {
    private static final UUID OWNER =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");

    @Mock ScoreLedgerRepository ledgerRepository;

    ScoreLedgerCommandService service;

    @BeforeEach
    void setUp() {
        service = new ScoreLedgerCommandService(
                ledgerRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(ledgerRepository.save(any(ScoreLedgerEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsAnOwnedInProgressLedgerWithTwoToSixNormalizedPlayers() {
        ScoreLedgerDetailResponse response = service.create(
                OWNER,
                List.of(
                        new CreateScorePlayer("  WhimSeeker  ", true),
                        new CreateScorePlayer("牌友甲", false),
                        new CreateScorePlayer("牌友乙", false)));

        assertThat(response.status()).isEqualTo(ScoreLedgerStatus.IN_PROGRESS);
        assertThat(response.startedAt()).isEqualTo(NOW);
        assertThat(response.endedAt()).isNull();
        assertThat(response.roundCount()).isZero();
        assertThat(response.favorite()).isFalse();
        assertThat(response.players())
                .extracting(ScoreLedgerDetailResponse.Player::name)
                .containsExactly("WhimSeeker", "牌友甲", "牌友乙");
        assertThat(response.players())
                .extracting(ScoreLedgerDetailResponse.Player::ownerPlayer)
                .containsExactly(true, false, false);
        assertThat(response.players())
                .extracting(ScoreLedgerDetailResponse.Player::totalScore)
                .containsOnly(0L);
    }

    @Test
    void rejectsFewerThanTwoOrMoreThanSixPlayers() {
        assertInvalid(List.of(new CreateScorePlayer("自己", true)));
        assertInvalid(List.of(
                new CreateScorePlayer("1", true),
                new CreateScorePlayer("2", false),
                new CreateScorePlayer("3", false),
                new CreateScorePlayer("4", false),
                new CreateScorePlayer("5", false),
                new CreateScorePlayer("6", false),
                new CreateScorePlayer("7", false)));
    }

    @Test
    void rejectsBlankDuplicateAndMultipleOwnerPlayers() {
        assertInvalid(List.of(
                new CreateScorePlayer("自己", true),
                new CreateScorePlayer("  ", false)));
        assertInvalid(List.of(
                new CreateScorePlayer("自己", true),
                new CreateScorePlayer("自己 ", false)));
        assertInvalid(List.of(
                new CreateScorePlayer("甲", false),
                new CreateScorePlayer("乙", false)));
        assertInvalid(List.of(
                new CreateScorePlayer("甲", true),
                new CreateScorePlayer("乙", true)));
    }

    private void assertInvalid(List<CreateScorePlayer> players) {
        assertThatThrownBy(() -> service.create(OWNER, players))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.SCORE_LEDGER_INVALID);
    }
}
