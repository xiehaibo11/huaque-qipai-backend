package com.nanbei.entertainment.backend.gameplay.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 跨局累计的大结算数据。 */
record QaTaizhouTotalResult(int playCount, Map<Integer, SeatTotal> seats) {
    QaTaizhouTotalResult {
        seats = Map.copyOf(seats);
    }

    static QaTaizhouTotalResult empty(int chairCount) {
        Map<Integer, SeatTotal> seats = new LinkedHashMap<>();
        for (int seat = 1; seat <= chairCount; seat++) {
            seats.put(seat, SeatTotal.empty());
        }
        return new QaTaizhouTotalResult(0, seats);
    }

    QaTaizhouTotalResult recordRound(
            QaRoundTable.Outcome outcome,
            Map<Integer, QaTaizhouScorer.SeatScore> seatScores) {
        Map<Integer, SeatTotal> next = new LinkedHashMap<>();
        for (Map.Entry<Integer, SeatTotal> entry : seats.entrySet()) {
            int seat = entry.getKey();
            SeatTotal previous = entry.getValue();
            long delta = outcome.deltas().getOrDefault(seat, 0L);
            QaTaizhouScorer.SeatScore score =
                    seatScores.getOrDefault(seat, QaTaizhouScorer.zeroSeat());
            List<Long> rounds = new ArrayList<>(previous.roundWinLost());
            rounds.add(delta);
            boolean winner = outcome.winnerSeat() == seat;
            boolean newMaxFan = score.fan() > previous.maxFanNum();
            next.put(
                    seat,
                    new SeatTotal(
                            rounds,
                            Math.max(previous.maxHuCount(), score.totalHu()),
                            Math.max(previous.maxFanNum(), score.fan()),
                            newMaxFan ? score.fanNames().size() : previous.maxFanCount(),
                            newMaxFan ? score.fanNames() : previous.maxFanNames(),
                            previous.winByOwn()
                                    + (winner && "ZIMO".equals(outcome.winType()) ? 1 : 0),
                            previous.winScoreNum() + (delta > 0 ? 1 : 0),
                            previous.jiePaoNum()
                                    + (winner && "DIANPAO".equals(outcome.winType()) ? 1 : 0),
                            previous.discardNum()
                                    + (outcome.discarderSeat() != null
                                                    && outcome.discarderSeat() == seat
                                            ? 1
                                            : 0),
                            Math.max(previous.maxScore(), delta),
                            previous.laZiNum() + (score.totalHu() >= 100 ? 1 : 0),
                            previous.chengBaoNum()
                                    + ("EPS_CHENGBAO"
                                                    .equals(outcome.endStates().get(seat))
                                            ? 1
                                            : 0)));
        }
        return new QaTaizhouTotalResult(playCount + 1, next);
    }

    record SeatTotal(
            List<Long> roundWinLost,
            int maxHuCount,
            int maxFanNum,
            int maxFanCount,
            List<String> maxFanNames,
            int winByOwn,
            int winScoreNum,
            int jiePaoNum,
            int discardNum,
            long maxScore,
            int laZiNum,
            int chengBaoNum) {
        SeatTotal {
            roundWinLost = List.copyOf(roundWinLost);
            maxFanNames = List.copyOf(maxFanNames);
        }

        static SeatTotal empty() {
            return new SeatTotal(List.of(), 0, 0, 0, List.of(), 0, 0, 0, 0, 0L, 0, 0);
        }
    }
}
