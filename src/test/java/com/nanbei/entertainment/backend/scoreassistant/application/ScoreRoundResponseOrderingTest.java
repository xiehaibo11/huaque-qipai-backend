package com.nanbei.entertainment.backend.scoreassistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerEntity;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerPlayerEntity;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerRoundEntity;
import com.nanbei.entertainment.backend.scoreassistant.domain.ScoreLedgerRoundScoreEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScoreRoundResponseOrderingTest {
    @Test
    void ordersDatabaseLoadedScoresByPlayerPosition() {
        ScoreLedgerPlayerEntity first = player((short) 1);
        ScoreLedgerPlayerEntity second = player((short) 2);
        ScoreLedgerRoundScoreEntity secondScore = score(second, -18);
        ScoreLedgerRoundScoreEntity firstScore = score(first, 18);
        ScoreLedgerRoundEntity round = mock(ScoreLedgerRoundEntity.class);
        when(round.getId()).thenReturn(UUID.randomUUID());
        when(round.getRoundNumber()).thenReturn(1);
        when(round.getRecordedAt()).thenReturn(Instant.EPOCH);
        when(round.getScores()).thenReturn(List.of(secondScore, firstScore));

        ScoreRoundResponse response = ScoreRoundResponse.from(round);

        assertThat(response.scores())
                .extracting(ScoreRoundResponse.Score::playerId)
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void ordersDatabaseLoadedLedgerPlayersByPosition() {
        ScoreLedgerPlayerEntity first = player((short) 1);
        ScoreLedgerPlayerEntity second = player((short) 2);
        ScoreLedgerEntity ledger = mock(ScoreLedgerEntity.class);
        when(ledger.getId()).thenReturn(UUID.randomUUID());
        when(ledger.getPlayers()).thenReturn(List.of(second, first));

        assertThat(ScoreLedgerSummary.from(ledger).players())
                .extracting(ScoreLedgerSummary.Player::playerId)
                .containsExactly(first.getId(), second.getId());
        assertThat(ScoreLedgerDetailResponse.from(ledger, List.of()).players())
                .extracting(ScoreLedgerDetailResponse.Player::playerId)
                .containsExactly(first.getId(), second.getId());
    }

    private static ScoreLedgerPlayerEntity player(short position) {
        ScoreLedgerPlayerEntity player = mock(ScoreLedgerPlayerEntity.class);
        when(player.getId()).thenReturn(UUID.randomUUID());
        when(player.getPosition()).thenReturn(position);
        return player;
    }

    private static ScoreLedgerRoundScoreEntity score(
            ScoreLedgerPlayerEntity player, long delta) {
        ScoreLedgerRoundScoreEntity score = mock(ScoreLedgerRoundScoreEntity.class);
        when(score.getPlayer()).thenReturn(player);
        when(score.getScoreDelta()).thenReturn(delta);
        when(score.getTotalAfter()).thenReturn(delta);
        return score;
    }
}
