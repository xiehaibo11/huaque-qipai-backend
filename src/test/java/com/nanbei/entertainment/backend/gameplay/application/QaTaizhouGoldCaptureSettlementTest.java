package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 2026-08-20 原版 30400 金币场实机抓包的三桌可完整复算结算。 */
class QaTaizhouGoldCaptureSettlementTest {

    @Test
    void roundOneAppliesCapturedBaseScoreToEveryPairSettlement() {
        QaRoundTable table = table(2, 60, Map.of(1, "PASS", 2, "PASS", 3, "PASS", 4, "PASS"));

        assertThat(QaTaizhouSettlementMatrix.deltas(table, 1, scores(12, 0, 6, 2)))
                .containsExactly(
                        Map.entry(1, 1_440L),
                        Map.entry(2, -1_200L),
                        Map.entry(3, 120L),
                        Map.entry(4, -360L));
    }

    @Test
    void roundTwoMatchesCapturedDealerAndIdleSeatPayments() {
        QaRoundTable table = table(4, 60, Map.of(1, "PASS", 2, "PASS", 3, "PASS", 4, "PASS"));

        assertThat(QaTaizhouSettlementMatrix.deltas(table, 3, scores(4, 2, 10, 0)))
                .containsExactly(
                        Map.entry(1, 0L),
                        Map.entry(2, -240L),
                        Map.entry(3, 1_200L),
                        Map.entry(4, -960L));
    }

    @Test
    void roundFourDoublesEveryPairThatIncludesTheCapturedDefaultPlayer() {
        QaRoundTable table = table(2, 100, Map.of(1, "PASS", 2, "PASS", 3, "PASS", 4, "DEFAULT"));

        assertThat(QaTaizhouSettlementMatrix.deltas(table, 3, scores(4, 12, 12, 2)))
                .containsExactly(
                        Map.entry(1, -1_200L),
                        Map.entry(2, 1_600L),
                        Map.entry(3, 3_000L),
                        Map.entry(4, -3_400L));
    }

    @Test
    void bankruptPlayerCannotLoseMoreCoinsThanTheCapturedOpeningBalance() {
        QaRoundTable table = table(3, 60, Map.of(1, "PASS", 2, "PASS", 3, "DEFAULT", 4, "PASS"));
        table.goldMode = true;
        table.openingCoinsBySeat.putAll(Map.of(1, 2_975L, 2, 3_337L, 3, 3_555L, 4, 4_018L));

        Map<Integer, Long> deltas =
                QaTaizhouSettlementMatrix.deltas(table, 4, scores(0, 22, 2, 24));

        assertThat(deltas.values().stream().mapToLong(Long::longValue).sum()).isZero();
        assertThat(deltas.get(3)).isEqualTo(-3_555L);
        assertThat(deltas).allSatisfy(
                (seat, delta) -> assertThat(delta).isGreaterThanOrEqualTo(-table.openingCoinsBySeat.get(seat)));
    }

    private static QaRoundTable table(
            int dealerSeat, long baseScore, Map<Integer, String> choices) {
        QaRoundTable table = QaRoundTable.newRound(4, dealerSeat, 1, Set.of());
        table.baseScore = baseScore;
        table.choices().putAll(choices);
        return table;
    }

    private static Map<Integer, QaTaizhouScorer.SeatScore> scores(int... totalHu) {
        Map<Integer, QaTaizhouScorer.SeatScore> scores = new LinkedHashMap<>();
        for (int index = 0; index < totalHu.length; index++) {
            int hu = totalHu[index];
            scores.put(index + 1, new QaTaizhouScorer.SeatScore(hu, 0, hu, 0, 0, false, false));
        }
        return scores;
    }
}
