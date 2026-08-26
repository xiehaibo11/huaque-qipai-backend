package com.nanbei.entertainment.backend.gameplay.application;

import java.util.LinkedHashMap;
import java.util.Map;

/** 台州大众玩法第六节结算矩阵（按文档实现；不是恢复出的原版服务端源码）。 */
final class QaTaizhouSettlementMatrix {
    private static final int MAX_HU = 100;

    private QaTaizhouSettlementMatrix() {}

    static Map<Integer, Long> deltas(
            QaRoundTable table,
            int winnerSeat,
            Map<Integer, QaTaizhouScorer.SeatScore> seatScores) {
        Map<Integer, Long> deltas = zeroDeltas(table);
        if (winnerSeat < 1) {
            return deltas;
        }
        collectWinnerPayments(table, winnerSeat, seatScores, deltas);
        settleIdlePairs(table, winnerSeat, seatScores, deltas);
        if (table.baoPaiSeat != null) {
            deltas = QaTaizhouBaoPai.reassign(deltas, table.baoPaiSeat);
        }
        return table.goldMode ? capGoldLosses(table, deltas) : deltas;
    }

    /**
     * 抄底保护：金币场玩家最多输掉开桌时持有的金币；不足部分按赢家原始收益占比缩减。
     * 原厂抓包证明会执行抄底，但只有一个破产样本，不足以恢复其二次舍入次序；这里保持
     * 服务端零和及钱包不透支，待更多样本只替换本函数。
     */
    private static Map<Integer, Long> capGoldLosses(
            QaRoundTable table, Map<Integer, Long> rawDeltas) {
        Map<Integer, Long> capped = new LinkedHashMap<>();
        long affordableLosses = 0L;
        long rawWinnings = 0L;
        for (int seat = 1; seat <= table.chairCount; seat++) {
            long raw = rawDeltas.getOrDefault(seat, 0L);
            if (raw < 0L) {
                long balance = table.openingCoinsBySeat.getOrDefault(seat, 0L);
                long loss = Math.min(Math.negateExact(raw), balance);
                capped.put(seat, -loss);
                affordableLosses = Math.addExact(affordableLosses, loss);
            } else {
                capped.put(seat, 0L);
                rawWinnings = Math.addExact(rawWinnings, raw);
            }
        }
        if (rawWinnings == 0L) {
            return capped;
        }
        long assigned = 0L;
        for (int seat = 1; seat <= table.chairCount; seat++) {
            long raw = rawDeltas.getOrDefault(seat, 0L);
            if (raw > 0L) {
                long win = Math.multiplyExact(raw, affordableLosses) / rawWinnings;
                capped.put(seat, win);
                assigned = Math.addExact(assigned, win);
            }
        }
        long remainder = affordableLosses - assigned;
        for (int seat = 1; remainder > 0L && seat <= table.chairCount; seat++) {
            if (rawDeltas.getOrDefault(seat, 0L) > 0L) {
                capped.put(seat, capped.get(seat) + 1L);
                remainder--;
            }
        }
        return capped;
    }

    private static Map<Integer, Long> zeroDeltas(QaRoundTable table) {
        Map<Integer, Long> deltas = new LinkedHashMap<>();
        for (int seat = 1; seat <= table.chairCount; seat++) {
            deltas.put(seat, 0L);
        }
        return deltas;
    }

    private static void collectWinnerPayments(
            QaRoundTable table,
            int winnerSeat,
            Map<Integer, QaTaizhouScorer.SeatScore> seatScores,
            Map<Integer, Long> deltas) {
        for (int seat = 1; seat <= table.chairCount; seat++) {
            if (seat != winnerSeat) {
                transfer(
                        deltas,
                        seat,
                        winnerSeat,
                        scaledPayment(
                                table,
                                seat,
                                winnerSeat,
                                winnerPayment(table, winnerSeat, seat, seatScores)));
            }
        }
    }

    private static long winnerPayment(
            QaRoundTable table,
            int winnerSeat,
            int payerSeat,
            Map<Integer, QaTaizhouScorer.SeatScore> seatScores) {
        int winnerHu = seatScores.get(winnerSeat).totalHu();
        if (winnerHu >= MAX_HU) {
            return seatScores.get(payerSeat).totalHu() >= MAX_HU ? 0L : MAX_HU;
        }
        if (winnerSeat == table.dealerSeat || payerSeat == table.dealerSeat) {
            return winnerHu;
        }
        return half(winnerHu);
    }

    private static void settleIdlePairs(
            QaRoundTable table,
            int winnerSeat,
            Map<Integer, QaTaizhouScorer.SeatScore> seatScores,
            Map<Integer, Long> deltas) {
        for (int left = 1; left <= table.chairCount; left++) {
            if (left == winnerSeat) {
                continue;
            }
            for (int right = left + 1; right <= table.chairCount; right++) {
                if (right != winnerSeat) {
                    settleIdlePair(table, deltas, seatScores, left, right);
                }
            }
        }
    }

    private static void settleIdlePair(
            QaRoundTable table,
            Map<Integer, Long> deltas,
            Map<Integer, QaTaizhouScorer.SeatScore> seatScores,
            int leftSeat,
            int rightSeat) {
        int leftHu = seatScores.get(leftSeat).totalHu();
        int rightHu = seatScores.get(rightSeat).totalHu();
        if (leftHu >= MAX_HU && rightHu >= MAX_HU || leftHu == rightHu) {
            return;
        }
        if (leftHu >= MAX_HU) {
            transfer(deltas, rightSeat, leftSeat, scaledPayment(table, rightSeat, leftSeat, MAX_HU));
            return;
        }
        if (rightHu >= MAX_HU) {
            transfer(deltas, leftSeat, rightSeat, scaledPayment(table, leftSeat, rightSeat, MAX_HU));
            return;
        }
        int diff = Math.abs(leftHu - rightHu);
        long payment =
                leftSeat == table.dealerSeat || rightSeat == table.dealerSeat ? diff : half(diff);
        if (leftHu > rightHu) {
            transfer(deltas, rightSeat, leftSeat, scaledPayment(table, rightSeat, leftSeat, payment));
        } else {
            transfer(deltas, leftSeat, rightSeat, scaledPayment(table, leftSeat, rightSeat, payment));
        }
    }

    private static long scaledPayment(
            QaRoundTable table, int leftSeat, int rightSeat, long huPayment) {
        long payment = Math.multiplyExact(huPayment, table.baseScore);
        payment = Math.multiplyExact(payment, choiceMultiplier(table, leftSeat));
        return Math.multiplyExact(payment, choiceMultiplier(table, rightSeat));
    }

    private static long choiceMultiplier(QaRoundTable table, int seat) {
        return switch (table.choices().getOrDefault(seat, "PASS")) {
            case "DEFAULT", "ADD" -> 2L;
            case "SUPER" -> 4L;
            default -> 1L;
        };
    }

    private static void transfer(
            Map<Integer, Long> deltas, int payerSeat, int receiverSeat, long payment) {
        if (payment <= 0L) {
            return;
        }
        deltas.put(payerSeat, deltas.get(payerSeat) - payment);
        deltas.put(receiverSeat, deltas.get(receiverSeat) + payment);
    }

    private static long half(int hu) {
        return Math.round(hu / 2.0d);
    }
}
