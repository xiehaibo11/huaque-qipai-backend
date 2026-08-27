package com.nanbei.entertainment.backend.gameplay.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 台州大众玩法胡数/番数结算（按本仓库玩法文档实现；不是恢复出的原版服务端源码）。 */
final class QaTaizhouScorer {
    private static final int MAX_HU = 100;
    private static final Set<Integer> FAN_DRAGONS = Set.of(0x51, 0x52);
    /** 听牌预览的和牌方式：既不是自摸也不是点炮，只取牌型本身的台与胡。 */
    private static final String TING_PREVIEW = "TING_PREVIEW";

    private QaTaizhouScorer() {}

    record SeatScore(
            int handHu,
            int tai,
            int totalHu,
            int fan,
            int gangScore,
            boolean hasCaishen,
            boolean hasCaishenRestore,
            List<String> fanNames) {
        SeatScore {
            fanNames = List.copyOf(fanNames);
        }

        SeatScore(
                int handHu,
                int tai,
                int totalHu,
                int fan,
                int gangScore,
                boolean hasCaishen,
                boolean hasCaishenRestore) {
            this(
                    handHu,
                    tai,
                    totalHu,
                    fan,
                    gangScore,
                    hasCaishen,
                    hasCaishenRestore,
                    List.of());
        }
    }

    record RoundScore(Map<Integer, Long> deltas, Map<Integer, SeatScore> seatScores) {
        RoundScore {
            deltas = Map.copyOf(deltas);
            seatScores = Map.copyOf(seatScores);
        }
    }

    static RoundScore score(
            QaRoundTable table, int winnerSeat, String winType, Integer discarderSeat) {
        return score(table, winnerSeat, winType, discarderSeat, 0);
    }

    static RoundScore score(
            QaRoundTable table,
            int winnerSeat,
            String winType,
            Integer discarderSeat,
            int winningTile) {
        Map<Integer, SeatScore> seatScores = new LinkedHashMap<>();
        for (int seat = 1; seat <= table.chairCount; seat++) {
            seatScores.put(
                    seat,
                    scoreSeat(
                            table,
                            seat,
                            seat == winnerSeat,
                            winType,
                            seat == winnerSeat ? winningTile : 0));
        }
        return new RoundScore(
                QaTaizhouSettlementMatrix.deltas(table, winnerSeat, seatScores), seatScores);
    }

    static SeatScore zeroSeat() {
        return new SeatScore(0, 0, 0, 0, 0, false, false, List.of());
    }

    /**
     * 听牌提示用：按一手假想的和牌牌（已含所胡的那张）给出台与胡，供
     * {@code msgAllWaitInfo} 的 {@code nFanPoint/nHuPoint} 下发。不带自摸加分，
     * 因为摸牌还是点炮此刻未知。
     */
    static SeatScore tingPreview(QaRoundTable table, int seat, List<Integer> winningHand) {
        return scoreConcealed(table, seat, winningHand, true, TING_PREVIEW);
    }

    private static SeatScore scoreSeat(
            QaRoundTable table,
            int seat,
            boolean winner,
            String winType,
            int winningTile) {
        List<Integer> concealed = new ArrayList<>(table.hands().get(seat));
        if (winner && "DIANPAO".equals(winType) && table.lastDiscard != null) {
            concealed.add(table.lastDiscard.tile());
        } else if (winner && "QIANGGANG".equals(winType)) {
            concealed.add(winningTile);
        }
        return scoreConcealed(table, seat, concealed, winner, winType);
    }

    private static SeatScore scoreConcealed(
            QaRoundTable table,
            int seat,
            List<Integer> concealed,
            boolean winner,
            String winType) {
        ScoreParts parts = new ScoreParts();
        int seatWind = seatWind(seat, table.dealerSeat);
        boolean exposedAllTripletLike = true;
        for (QaRoundTable.Meld meld : table.melds().get(seat)) {
            if (!scoreMeld(parts, meld, seatWind)) {
                exposedAllTripletLike = false;
            }
        }

        QaTaizhouHandArrangement.Arrangement arrangement =
                winner
                        ? QaTaizhouHandArrangement.best(
                                normalizedTiles(concealed, table.jokerRule), seatWind)
                        : null;
        if (arrangement == null) {
            scoreLooseConcealed(parts, concealed, seatWind, table.jokerRule);
        } else {
            parts.points += arrangement.points();
            parts.fan += arrangement.fan();
            if (winner && exposedAllTripletLike && arrangement.sequenceCount() == 0) {
                parts.points += 2; // 对对胡
            }
        }

        if (winner && "ZIMO".equals(winType)) {
            parts.points += 2;
        }
        boolean hasCaishen = table.jokerRule.hasJoker(concealed);
        boolean hasCaishenRestore = table.jokerRule.hasInstead(concealed);
        if (winner) {
            int suitFan = suitFan(concealed, table.melds().get(seat), table.jokerRule);
            parts.fan += suitFan;
            if (suitFan == 3) {
                parts.fanNames.add("清一色");
            } else if (suitFan == 1) {
                parts.fanNames.add("混一色");
            }
            if (!hasCaishen) {
                parts.fan += 1; // 无得
                parts.fanNames.add("无得");
            }
            if (hasCaishenRestore) {
                parts.fan += 1;
                parts.fanNames.add("得还原");
            }
        }
        int handHu = winner ? 10 + parts.points : parts.points;
        int totalHu = capped(handHu, parts.fan);
        return new SeatScore(
                handHu,
                parts.fan,
                totalHu,
                parts.fan,
                parts.gangScore,
                hasCaishen,
                hasCaishenRestore,
                parts.fanNames);
    }

    private static boolean scoreMeld(ScoreParts parts, QaRoundTable.Meld meld, int seatWind) {
        String combType = meld.combType();
        int tile = firstPlayable(meld.tiles());
        if (tile == QaTaizhouTiles.NO_TILE || "CHOW".equals(combType)) {
            return false;
        }
        if ("PONG".equals(combType)) {
            int point = tripletPoint(tile, false);
            parts.points += point;
            addTripletFan(parts, tile, seatWind);
            return true;
        }
        if ("CONCEALED_KONG".equals(combType)) {
            int point = kongPoint(tile, true);
            parts.points += point;
            parts.gangScore += point;
            addTripletFan(parts, tile, seatWind);
            return true;
        }
        if ("EXPOSED_KONG".equals(combType) || "FILL_KONG".equals(combType)) {
            int point = kongPoint(tile, false);
            parts.points += point;
            parts.gangScore += point;
            addTripletFan(parts, tile, seatWind);
            return true;
        }
        return false;
    }

    private static void scoreLooseConcealed(
            ScoreParts parts,
            List<Integer> concealed,
            int seatWind,
            QaTaizhouJokerRule jokerRule) {
        Map<Integer, Integer> counts = new java.util.TreeMap<>();
        for (int tile : concealed) {
            if (!jokerRule.isJoker(tile)) {
                int ordinaryTile = jokerRule.normalizedOrdinaryTile(tile);
                if (QaTaizhouTiles.isPlayable(ordinaryTile)) {
                    counts.merge(ordinaryTile, 1, Integer::sum);
                }
            }
        }
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            int tile = entry.getKey();
            int count = entry.getValue();
            if (count >= 3) {
                parts.points += tripletPoint(tile, true);
                addTripletFan(parts, tile, seatWind);
            } else if (count >= 2) {
                parts.points += pairPoint(tile, seatWind);
            }
        }
    }

    private static int capped(int handHu, int fan) {
        long total = handHu;
        for (int index = 0; index < fan && total < MAX_HU; index++) {
            total *= 2L;
        }
        return (int) Math.min(MAX_HU, total);
    }

    private static int kongPoint(int tile, boolean concealed) {
        if (concealed) {
            return terminalOrHonour(tile) ? 32 : 16;
        }
        return terminalOrHonour(tile) ? 16 : 8;
    }

    static int tripletPoint(int tile, boolean concealed) {
        if (concealed) {
            return terminalOrHonour(tile) ? 8 : 4;
        }
        return terminalOrHonour(tile) ? 4 : 2;
    }

    static int pairPoint(int tile, int seatWind) {
        return tile == seatWind || tile == 0x51 || tile == 0x52 || tile == 0x53 ? 2 : 0;
    }

    static int fanForTriplet(int tile, int seatWind) {
        return FAN_DRAGONS.contains(tile) || tile == seatWind ? 1 : 0;
    }

    static String fanNameForTriplet(int tile, int seatWind) {
        if (tile == 0x51) {
            return "红中";
        }
        if (tile == 0x52) {
            return "发财";
        }
        if (tile == seatWind) {
            return "门风";
        }
        throw new IllegalArgumentException("tile has no triplet fan " + tile);
    }

    private static void addTripletFan(ScoreParts parts, int tile, int seatWind) {
        int fan = fanForTriplet(tile, seatWind);
        if (fan > 0) {
            parts.fan += fan;
            parts.fanNames.add(fanNameForTriplet(tile, seatWind));
        }
    }

    private static int suitFan(
            List<Integer> concealed,
            List<QaRoundTable.Meld> melds,
            QaTaizhouJokerRule jokerRule) {
        int suit = 0;
        boolean honour = false;
        for (int tile : concealed) {
            int tileSuit = scoringSuit(tile, jokerRule);
            if (tileSuit > 0) {
                suit = suit == 0 ? tileSuit : suit;
                if (suit != tileSuit) {
                    return 0;
                }
            } else if (QaTaizhouTiles.isHonour(tile)) {
                honour = true;
            }
        }
        for (QaRoundTable.Meld meld : melds) {
            for (int tile : meld.tiles()) {
                int tileSuit = scoringSuit(tile, jokerRule);
                if (tileSuit > 0) {
                    suit = suit == 0 ? tileSuit : suit;
                    if (suit != tileSuit) {
                        return 0;
                    }
                } else if (QaTaizhouTiles.isHonour(tile)) {
                    honour = true;
                }
            }
        }
        if (suit == 0) {
            return 0;
        }
        return honour ? 1 : 3;
    }

    private static int scoringSuit(int tile, QaTaizhouJokerRule jokerRule) {
        if (jokerRule.isJoker(tile)) {
            return 0;
        }
        int ordinaryTile = jokerRule.normalizedOrdinaryTile(tile);
        return QaTaizhouTiles.isSuited(ordinaryTile)
                ? QaTaizhouTiles.suitOf(ordinaryTile)
                : 0;
    }

    private static boolean terminalOrHonour(int tile) {
        return QaTaizhouTiles.isHonour(tile)
                || (QaTaizhouTiles.isSuited(tile)
                        && (QaTaizhouTiles.rankOf(tile) == 1
                                || QaTaizhouTiles.rankOf(tile) == 9));
    }

    private static int seatWind(int seat, int dealerSeat) {
        return 0x41 + Math.floorMod(seat - dealerSeat, 4);
    }

    private static List<Integer> normalizedTiles(
            List<Integer> concealed, QaTaizhouJokerRule jokerRule) {
        List<Integer> normalized = new ArrayList<>(concealed.size());
        for (int tile : concealed) {
            normalized.add(
                    jokerRule.isJoker(tile)
                            ? QaTaizhouTiles.JOKER
                            : jokerRule.normalizedOrdinaryTile(tile));
        }
        return normalized;
    }

    private static int firstPlayable(List<Integer> tiles) {
        for (int tile : tiles) {
            if (QaTaizhouTiles.isPlayable(tile)) {
                return tile;
            }
        }
        return QaTaizhouTiles.NO_TILE;
    }

    private static final class ScoreParts {
        int points;
        int fan;
        int gangScore;
        final List<String> fanNames = new ArrayList<>();
    }
}
