package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TING_INFO 听牌映射（南北自建 QA 规则，非原版服务端算法）的纯计算契约。 */
class QaTingInfoCalculatorTest {
    private static final int SEAT = 1;

    private final QaTingInfoCalculator calculator = new QaTingInfoCalculator();

    @Test
    void everyDiscardEntryListsTheExactHuTargets() {
        // 3 组顺子 + 东风刻 + 一对西风：14 张。
        List<Integer> hand =
                List.of(
                        0x11, 0x12, 0x13, 0x21, 0x22, 0x23, 0x31, 0x32, 0x33, 0x41, 0x41, 0x41,
                        0x42, 0x42);

        List<QaRoundTable.TingEntry> entries = calculator.compute(tableWith(hand), SEAT);

        assertThat(entries).extracting(QaRoundTable.TingEntry::discard)
                .containsExactly(0x11, 0x12, 0x13, 0x21, 0x22, 0x23, 0x31, 0x32, 0x33, 0x41, 0x42);
        assertThat(entry(entries, 0x11).huTargets()).containsExactly(0x11, 0x14);
        assertThat(entry(entries, 0x13).huTargets()).containsExactly(0x13);
        assertThat(entry(entries, 0x41).huTargets()).containsExactly(0x41, 0x42);
        assertThat(entry(entries, 0x42).huTargets()).containsExactly(0x42);
    }

    @Test
    void aHandThatCannotTingYieldsAnEmptyMapping() {
        // 全对子加两张孤字牌：打出任何一张后一巡内无可听。
        List<Integer> hand =
                List.of(
                        0x11, 0x11, 0x19, 0x19, 0x21, 0x21, 0x29, 0x29, 0x31, 0x31, 0x39, 0x39,
                        0x41, 0x51);

        assertThat(calculator.compute(tableWith(hand), SEAT)).isEmpty();
    }

    @Test
    void handsWithAnInvalidSizeForDrawingYieldAnEmptyMapping() {
        assertThat(calculator.compute(tableWith(List.of(0x11, 0x12, 0x13)), SEAT)).isEmpty();
        assertThat(calculator.compute(tableWith(List.of()), SEAT)).isEmpty();
    }

    @Test
    void aTileValueWithAllFourCopiesInHandIsNotAHuTarget() {
        // 一万四张全在手：打出一万后听牌目标里不能再有一万（物理上无第 5 张）。
        List<Integer> hand =
                List.of(
                        0x11, 0x11, 0x11, 0x11, 0x21, 0x22, 0x23, 0x31, 0x32, 0x33, 0x12, 0x13,
                        0x41, 0x41);

        List<QaRoundTable.TingEntry> entries = calculator.compute(tableWith(hand), SEAT);

        assertThat(entry(entries, 0x11).huTargets()).doesNotContain(0x11);
    }

    @Test
    void anExhaustedBudgetDegradesToAnEmptyMapping() {
        List<Integer> hand =
                List.of(
                        0x11, 0x12, 0x13, 0x21, 0x22, 0x23, 0x31, 0x32, 0x33, 0x41, 0x41, 0x41,
                        0x42, 0x42);

        assertThat(calculator.compute(tableWith(hand), SEAT, -1L)).isEmpty();
    }

    @Test
    void aFullDealtHandComputesWithinTheFiftyMillisecondBudget() {
        List<Integer> hand =
                List.of(
                        0x11, 0x14, 0x17, 0x19, 0x22, 0x25, 0x28, 0x33, 0x36, 0x39, 0x42, 0x44,
                        0x51, 0x53);

        long start = System.nanoTime();
        calculator.compute(tableWith(hand), SEAT);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMillis).isLessThan(50L);
    }

    @Test
    void everyHuTargetCarriesItsOwnFanAndHuPoints() {
        // 3 组顺子 + 东风刻 + 一对西风：14 张，庄位在 1 座即门风东。
        List<Integer> hand =
                List.of(
                        0x11, 0x12, 0x13, 0x21, 0x22, 0x23, 0x31, 0x32, 0x33, 0x41, 0x41, 0x41,
                        0x42, 0x42);

        QaRoundTable table = tableWith(hand);
        QaRoundTable.TingEntry entry = entry(calculator.compute(table, SEAT), 0x11);

        assertThat(entry.fanPoints()).hasSameSizeAs(entry.huTargets());
        assertThat(entry.huPoints()).hasSameSizeAs(entry.huTargets());
        for (int index = 0; index < entry.huTargets().size(); index++) {
            List<Integer> winningHand = new ArrayList<>(hand);
            winningHand.remove(Integer.valueOf(0x11));
            winningHand.add(entry.huTargets().get(index));
            QaTaizhouScorer.SeatScore expected =
                    QaTaizhouScorer.tingPreview(table, SEAT, winningHand);
            assertThat(entry.fanPoints().get(index)).isEqualTo(expected.tai());
            assertThat(entry.huPoints().get(index)).isEqualTo(expected.totalHu());
            assertThat(entry.huPoints().get(index)).isPositive();
        }
    }

    private static QaRoundTable tableWith(List<Integer> hand) {
        QaRoundTable table = QaRoundTable.newRound(4, SEAT, 1, List.of());
        table.hands().get(SEAT).addAll(hand);
        return table;
    }

    private static QaRoundTable.TingEntry entry(List<QaRoundTable.TingEntry> entries, int discard) {
        return entries.stream()
                .filter(candidate -> candidate.discard() == discard)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no ting entry for discard " + discard));
    }
}
