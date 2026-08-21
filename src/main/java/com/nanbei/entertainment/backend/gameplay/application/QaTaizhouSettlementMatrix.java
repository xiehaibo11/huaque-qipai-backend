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
        return deltas;
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
                transfer(deltas, seat, winnerSeat, winnerPayment(table, winnerSeat, seat, seatScores));
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
            transfer(deltas, rightSeat, leftSeat, MAX_HU);
            return;
        }
        if (rightHu >= MAX_HU) {
            transfer(deltas, leftSeat, rightSeat, MAX_HU);
            return;
        }
        int diff = Math.abs(leftHu - rightHu);
        long payment =
                leftSeat == table.dealerSeat || rightSeat == table.dealerSeat ? diff : half(diff);
        if (leftHu > rightHu) {
            transfer(deltas, rightSeat, leftSeat, payment);
        } else {
            transfer(deltas, leftSeat, rightSeat, payment);
        }
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
