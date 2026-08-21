package com.nanbei.entertainment.backend.gameplay.application;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TING_INFO 听牌映射计算（南北自建 QA 规则，非原版服务端算法）。
 *
 * <p>语义对齐原版 {@code msgTingMahInfo}（XY_ID 562）：对每个可打牌，移除该牌后剩余手牌
 * 对哪些牌值形成 N 副 + 1 将（沿用 {@link QaWinDetector} 的财神规则）；听的牌值按升序去重，
 * 物理上已见 4 张的牌值不再列入。只在出牌权窗口开启时为真人座位计算；
 * 计算预算 50ms，超时或异常降级为空映射并记日志，不阻断轮转。
 */
final class QaTingInfoCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(QaTingInfoCalculator.class);
    private static final long BUDGET_NANOS = 50_000_000L;
    /** 34 种基础牌值（万/条/筒 1-9、风 1-4、箭 1-3）；花牌、牌背与财神不是听牌目标。 */
    private static final List<Integer> HU_TARGET_VALUES = huTargetValues();

    /** 计算手牌的听牌映射；手牌张数不满足 3N+2、超时或异常时返回空列表。 */
    List<QaRoundTable.TingEntry> compute(List<Integer> hand) {
        return compute(hand, BUDGET_NANOS);
    }

    /** 预算可注入的入口（超时降级路径的契约测试用）。 */
    List<QaRoundTable.TingEntry> compute(List<Integer> hand, long budgetNanos) {
        long deadline = System.nanoTime() + budgetNanos;
        try {
            return computeWithin(hand, deadline);
        } catch (TingBudgetExceededException exception) {
            LOGGER.warn("TING_INFO 计算超过 50ms 预算，降级为空映射");
            return List.of();
        } catch (RuntimeException exception) {
            LOGGER.warn("TING_INFO 计算异常，降级为空映射: {}", exception.toString());
            return List.of();
        }
    }

    static List<QaRoundTable.TingEntry> computeWithin(List<Integer> hand, long deadlineNanos) {
        if (hand == null || hand.size() % 3 != 2) {
            return List.of();
        }
        TreeSet<Integer> discards = new TreeSet<>();
        for (int tile : hand) {
            if (QaTaizhouTiles.isPlayable(tile)) {
                discards.add(tile);
            }
        }
        List<QaRoundTable.TingEntry> entries = new ArrayList<>();
        for (int discard : discards) {
            if (System.nanoTime() - deadlineNanos > 0) {
                throw new TingBudgetExceededException();
            }
            List<Integer> remaining = new ArrayList<>(hand);
            remaining.remove(Integer.valueOf(discard));
            List<Integer> huTargets = new ArrayList<>();
            for (int candidate : HU_TARGET_VALUES) {
                // 自建：同种牌物理上只有 4 张；手里剩余 + 刚弃的这张已出墙 4 张时，
                // 不可能再摸到该牌值，不列入听牌目标。
                int seen = countOf(remaining, candidate) + (candidate == discard ? 1 : 0);
                if (seen >= 4) {
                    continue;
                }
                List<Integer> withTile = new ArrayList<>(remaining);
                withTile.add(candidate);
                if (QaWinDetector.canWin(withTile)) {
                    huTargets.add(candidate);
                }
            }
            if (!huTargets.isEmpty()) {
                entries.add(new QaRoundTable.TingEntry(discard, huTargets));
            }
        }
        return entries;
    }

    private static int countOf(List<Integer> tiles, int value) {
        int count = 0;
        for (int tile : tiles) {
            if (tile == value) {
                count++;
            }
        }
        return count;
    }

    private static List<Integer> huTargetValues() {
        List<Integer> values = new ArrayList<>(34);
        for (int suit : List.of(0x1, 0x2, 0x3)) {
            for (int rank = 1; rank <= 9; rank++) {
                values.add((suit << 4) + rank);
            }
        }
        for (int rank = 1; rank <= 4; rank++) {
            values.add((0x4 << 4) + rank);
        }
        for (int rank = 1; rank <= 3; rank++) {
            values.add((0x5 << 4) + rank);
        }
        return List.copyOf(values);
    }

    private static final class TingBudgetExceededException extends RuntimeException {}
}
