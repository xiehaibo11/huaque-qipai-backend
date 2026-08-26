package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QaTaizhouScorerTest {
    @Test
    void selfDrawWinnerUsesDocumentBasePointsFansAndIdleHalfPayments() {
        QaRoundTable table = table();
        table.hands()
                .get(2)
                .addAll(
                        List.of(
                                0x11, 0x11, 0x11,
                                0x22, 0x22, 0x22,
                                0x13, 0x14, 0x15,
                                0x31, 0x32, 0x33,
                                0x42, 0x42));

        QaTaizhouScorer.RoundScore score = QaTaizhouScorer.score(table, 2, "ZIMO", null);

        QaTaizhouScorer.SeatScore winner = score.seatScores().get(2);
        assertThat(winner.handHu()).isEqualTo(26);
        assertThat(winner.tai()).isEqualTo(1);
        assertThat(winner.totalHu()).isEqualTo(52);
        assertThat(winner.gangScore()).isZero();
        assertThat(winner.hasCaishen()).isFalse();
        assertThat(score.deltas())
                .containsEntry(1, -52L)
                .containsEntry(2, 104L)
                .containsEntry(3, -26L)
                .containsEntry(4, -26L);
    }

    @Test
    void laziWinnerDoesNotCollectFromAnotherLaziSeat() {
        QaRoundTable table = table();
        table.hands()
                .get(2)
                .addAll(
                        List.of(
                                0x11, 0x11, 0x11,
                                0x19, 0x19, 0x19,
                                0x41, 0x41, 0x41,
                                0x51, 0x51, 0x51,
                                0x42, 0x42));
        table.hands()
                .get(3)
                .addAll(
                        List.of(
                                0x43, 0x43));
        table.melds()
                .get(3)
                .addAll(
                        List.of(
                                new QaRoundTable.Meld(
                                        "CONCEALED_KONG", List.of(0x11, 0x11, 0x11, 0x11), 3),
                                new QaRoundTable.Meld(
                                        "CONCEALED_KONG", List.of(0x19, 0x19, 0x19, 0x19), 3),
                                new QaRoundTable.Meld(
                                        "CONCEALED_KONG", List.of(0x41, 0x41, 0x41, 0x41), 3),
                                new QaRoundTable.Meld(
                                        "CONCEALED_KONG", List.of(0x51, 0x51, 0x51, 0x51), 3)));

        QaTaizhouScorer.RoundScore score = QaTaizhouScorer.score(table, 2, "ZIMO", null);

        assertThat(score.seatScores().get(2).totalHu()).isEqualTo(100);
        assertThat(score.seatScores().get(3).totalHu()).isEqualTo(100);
        assertThat(score.deltas())
                .containsEntry(1, -200L)
                .containsEntry(2, 200L)
                .containsEntry(3, 200L)
                .containsEntry(4, -200L);
    }

    @Test
    void idleSeatsSettlePairwiseAfterWinnerCollections() {
        QaRoundTable table = table();
        table.hands()
                .get(2)
                .addAll(
                        List.of(
                                0x12, 0x13, 0x14,
                                0x22, 0x23, 0x24,
                                0x32, 0x33, 0x34,
                                0x15, 0x15, 0x15,
                                0x16, 0x16));
        table.hands().get(3).addAll(List.of(0x11, 0x11, 0x11));
        table.hands().get(4).addAll(List.of(0x51, 0x51, 0x51));

        QaTaizhouScorer.RoundScore score = QaTaizhouScorer.score(table, 2, "ZIMO", null);

        assertThat(score.seatScores().get(2).totalHu()).isEqualTo(32);
        assertThat(score.seatScores().get(3).totalHu()).isEqualTo(8);
        assertThat(score.seatScores().get(4).totalHu()).isEqualTo(16);
        assertThat(score.deltas())
                .containsEntry(1, -56L)
                .containsEntry(2, 64L)
                .containsEntry(3, -12L)
                .containsEntry(4, 4L);
    }

    @Test
    void dealerSeatIsEastForDoorWindScoringRegardlessOfItsAbsoluteSeatNumber() {
        QaRoundTable table = QaRoundTable.newRound(4, 3, 1, Set.of());
        table.hands()
                .get(3)
                .addAll(
                        List.of(
                                0x11, 0x11, 0x11,
                                0x23, 0x24, 0x25,
                                0x31, 0x32, 0x33,
                                0x41, 0x41, 0x41,
                                0x26, 0x26));

        QaTaizhouScorer.SeatScore winner =
                QaTaizhouScorer.score(table, 3, "ZIMO", null).seatScores().get(3);

        assertThat(winner.tai()).isEqualTo(2);
    }

    private static QaRoundTable table() {
        return QaRoundTable.newRound(4, 1, 1, Set.of());
    }
}
