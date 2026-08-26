package com.nanbei.entertainment.backend.gameplay.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 大结算的南北快照与原协议 msgTotalResult(1038) 投影。 */
final class QaTaizhouTotalResultProjection {
    private QaTaizhouTotalResultProjection() {}

    static Map<String, Object> project(QaRoundTable table) {
        QaTaizhouTotalResult total = table.totalResult;
        List<Map<String, Object>> seats = new ArrayList<>();
        List<List<Long>> roundWinLost = new ArrayList<>();
        List<Integer> maxHu = new ArrayList<>();
        List<Integer> maxFan = new ArrayList<>();
        List<Integer> maxFanCount = new ArrayList<>();
        List<List<Integer>> maxFanName = new ArrayList<>();
        List<Integer> selfDraws = new ArrayList<>();
        List<Integer> positiveRounds = new ArrayList<>();
        List<Integer> receivedDiscards = new ArrayList<>();
        List<Integer> discardedWins = new ArrayList<>();
        List<Long> maxScores = new ArrayList<>();
        List<Integer> laziCounts = new ArrayList<>();
        List<Integer> contractedCounts = new ArrayList<>();
        for (int seat = 1; seat <= table.chairCount; seat++) {
            QaTaizhouTotalResult.SeatTotal value =
                    total.seats().getOrDefault(seat, QaTaizhouTotalResult.SeatTotal.empty());
            seats.add(seatEntry(seat, value));
            roundWinLost.add(value.roundWinLost());
            maxHu.add(value.maxHuCount());
            maxFan.add(value.maxFanNum());
            maxFanCount.add(value.maxFanCount());
            maxFanName.add(value.maxFanNames().stream().map(QaTaizhouTotalResultProjection::fanCode).toList());
            selfDraws.add(value.winByOwn());
            positiveRounds.add(value.winScoreNum());
            receivedDiscards.add(value.jiePaoNum());
            discardedWins.add(value.discardNum());
            maxScores.add(value.maxScore());
            laziCounts.add(value.laZiNum());
            contractedCounts.add(value.chengBaoNum());
        }
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("XY_ID", 1038);
        original.put("playCount", total.playCount());
        original.put("boxRoomTotalWinLost", roundWinLost);
        original.put("maxHuCount", maxHu);
        original.put("maxFanNum", maxFan);
        original.put("maxFanCount", maxFanCount);
        original.put("maxFanName", maxFanName);
        original.put("show", true);
        original.put("nWinByOwn", selfDraws);
        original.put("nWinScoreNum", positiveRounds);
        original.put("nJiePaoNum", receivedDiscards);
        original.put("nDiscardNum", discardedWins);
        original.put("nMaxSorceOfTotal", maxScores);
        original.put("nLaZiNum", laziCounts);
        original.put("nChengBaoNum", contractedCounts);
        return Map.of(
                "playCount", total.playCount(),
                "show", true,
                "seats", seats,
                "originalMsgTotalResult", original);
    }

    private static Map<String, Object> seatEntry(
            int seat, QaTaizhouTotalResult.SeatTotal value) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("seatNumber", seat);
        entry.put("roundWinLost", value.roundWinLost());
        entry.put("maxHuCount", value.maxHuCount());
        entry.put("maxFanNum", value.maxFanNum());
        entry.put("maxFanCount", value.maxFanCount());
        entry.put("maxFanNames", value.maxFanNames());
        entry.put("winByOwn", value.winByOwn());
        entry.put("winScoreNum", value.winScoreNum());
        entry.put("jiePaoNum", value.jiePaoNum());
        entry.put("discardNum", value.discardNum());
        entry.put("maxScore", value.maxScore());
        entry.put("laZiNum", value.laZiNum());
        entry.put("chengBaoNum", value.chengBaoNum());
        return entry;
    }

    private static int fanCode(String name) {
        return switch (name) {
            case "红中" -> 1;
            case "发财" -> 2;
            case "门风" -> 3;
            case "清一色" -> 4;
            case "混一色" -> 5;
            case "无得" -> 6;
            case "得还原" -> 7;
            default -> throw new IllegalArgumentException("unknown fan name " + name);
        };
    }
}
