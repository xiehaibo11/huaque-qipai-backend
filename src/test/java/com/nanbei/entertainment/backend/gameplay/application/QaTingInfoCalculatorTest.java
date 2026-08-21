package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** TING_INFO 听牌映射（南北自建 QA 规则，非原版服务端算法）的纯计算契约。 */
class QaTingInfoCalculatorTest {
    private final QaTingInfoCalculator calculator = new QaTingInfoCalculator();

    @Test
    void everyDiscardEntryListsTheExactHuTargets() {
        // 3 组顺子 + 东风刻 + 一对西风：14 张。
        List<Integer> hand =
                List.of(
                        0x11, 0x12, 0x13, 0x21, 0x22, 0x23, 0x31, 0x32, 0x33, 0x41, 0x41, 0x41,
                        0x42, 0x42);

        List<QaRoundTable.TingEntry> entries = calculator.compute(hand);

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

        assertThat(calculator.compute(hand)).isEmpty();
    }

    @Test
    void handsWithAnInvalidSizeForDrawingYieldAnEmptyMapping() {
        assertThat(calculator.compute(List.of(0x11, 0x12, 0x13))).isEmpty();
        assertThat(calculator.compute(List.of())).isEmpty();
    }

    @Test
    void aTileValueWithAllFourCopiesInHandIsNotAHuTarget() {
        // 一万四张全在手：打出一万后听牌目标里不能再有一万（物理上无第 5 张）。
        List<Integer> hand =
                List.of(
                        0x11, 0x11, 0x11, 0x11, 0x21, 0x22, 0x23, 0x31, 0x32, 0x33, 0x12, 0x13,
                        0x41, 0x41);

        List<QaRoundTable.TingEntry> entries = calculator.compute(hand);

        assertThat(entry(entries, 0x11).huTargets()).doesNotContain(0x11);
    }

    @Test
    void anExhaustedBudgetDegradesToAnEmptyMapping() {
        List<Integer> hand =
                List.of(
                        0x11, 0x12, 0x13, 0x21, 0x22, 0x23, 0x31, 0x32, 0x33, 0x41, 0x41, 0x41,
                        0x42, 0x42);

        assertThat(calculator.compute(hand, -1L)).isEmpty();
    }

    @Test
    void aFullDealtHandComputesWithinTheFiftyMillisecondBudget() {
        List<Integer> hand =
                List.of(
                        0x11, 0x14, 0x17, 0x19, 0x22, 0x25, 0x28, 0x33, 0x36, 0x39, 0x42, 0x44,
                        0x51, 0x53);

        long start = System.nanoTime();
        calculator.compute(hand);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMillis).isLessThan(50L);
    }

    private static QaRoundTable.TingEntry entry(List<QaRoundTable.TingEntry> entries, int discard) {
        return entries.stream()
                .filter(candidate -> candidate.discard() == discard)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no ting entry for discard " + discard));
    }
}
