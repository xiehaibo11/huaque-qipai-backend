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

    private QaTaizhouScorer() {}

    record SeatScore(
            int handHu,
            int tai,
            int totalHu,
            int fan,
            int gangScore,
            boolean hasCaishen) {}

    record RoundScore(Map<Integer, Long> deltas, Map<Integer, SeatScore> seatScores) {
        RoundScore {
            deltas = Map.copyOf(deltas);
            seatScores = Map.copyOf(seatScores);
        }
    }

    static RoundScore score(
            QaRoundTable table, int winnerSeat, String winType, Integer discarderSeat) {
        Map<Integer, SeatScore> seatScores = new LinkedHashMap<>();
        for (int seat = 1; seat <= table.chairCount; seat++) {
            seatScores.put(seat, scoreSeat(table, seat, seat == winnerSeat, winType));
        }
        return new RoundScore(
                QaTaizhouSettlementMatrix.deltas(table, winnerSeat, seatScores), seatScores);
    }

    static SeatScore zeroSeat() {
        return new SeatScore(0, 0, 0, 0, 0, false);
    }

    private static SeatScore scoreSeat(
            QaRoundTable table, int seat, boolean winner, String winType) {
        List<Integer> concealed = new ArrayList<>(table.hands().get(seat));
        if (winner
                && "DIANPAO".equals(winType)
                && table.lastDiscard != null
                && table.lastDiscard.seat() != seat) {
            concealed.add(table.lastDiscard.tile());
        }

        ScoreParts parts = new ScoreParts();
        boolean exposedAllTripletLike = true;
        for (QaRoundTable.Meld meld : table.melds().get(seat)) {
            if (!scoreMeld(parts, meld, seat)) {
                exposedAllTripletLike = false;
            }
        }

        QaTaizhouHandArrangement.Arrangement arrangement =
                winner ? QaTaizhouHandArrangement.best(concealed, seat) : null;
        if (arrangement == null) {
            scoreLooseConcealed(parts, concealed, seat);
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
        boolean hasCaishen = hasCaishen(concealed);
        if (winner) {
            parts.fan += suitFan(concealed, table.melds().get(seat));
            if (!hasCaishen) {
                parts.fan += 1; // 无得
            }
        }
        int handHu = winner ? 10 + parts.points : parts.points;
        int totalHu = capped(handHu, parts.fan);
        return new SeatScore(handHu, parts.fan, totalHu, parts.fan, parts.gangScore, hasCaishen);
    }

    private static boolean scoreMeld(ScoreParts parts, QaRoundTable.Meld meld, int seat) {
        String combType = meld.combType();
        int tile = firstPlayable(meld.tiles());
        if (tile == QaTaizhouTiles.NO_TILE || "CHOW".equals(combType)) {
            return false;
        }
        if ("PONG".equals(combType)) {
            int point = tripletPoint(tile, false);
            parts.points += point;
            parts.fan += fanForTriplet(tile, seat);
            return true;
        }
        if ("CONCEALED_KONG".equals(combType)) {
            int point = kongPoint(tile, true);
            parts.points += point;
            parts.gangScore += point;
            parts.fan += fanForTriplet(tile, seat);
            return true;
        }
        if ("EXPOSED_KONG".equals(combType) || "FILL_KONG".equals(combType)) {
            int point = kongPoint(tile, false);
            parts.points += point;
            parts.gangScore += point;
            parts.fan += fanForTriplet(tile, seat);
            return true;
        }
        return false;
    }

    private static void scoreLooseConcealed(ScoreParts parts, List<Integer> concealed, int seat) {
        Map<Integer, Integer> counts = new java.util.TreeMap<>();
        for (int tile : concealed) {
            if (QaTaizhouTiles.isPlayable(tile)) {
                counts.merge(tile, 1, Integer::sum);
            }
        }
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            int tile = entry.getKey();
            int count = entry.getValue();
            if (count >= 3) {
                parts.points += tripletPoint(tile, true);
                parts.fan += fanForTriplet(tile, seat);
            } else if (count >= 2) {
                parts.points += pairPoint(tile, seat);
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

    static int pairPoint(int tile, int seat) {
        return tile == seatWind(seat) || tile == 0x51 || tile == 0x52 || tile == 0x53 ? 2 : 0;
    }

    static int fanForTriplet(int tile, int seat) {
        return FAN_DRAGONS.contains(tile) || tile == seatWind(seat) ? 1 : 0;
    }

    private static int suitFan(List<Integer> concealed, List<QaRoundTable.Meld> melds) {
        int suit = 0;
        boolean honour = false;
        for (int tile : concealed) {
            int tileSuit = scoringSuit(tile);
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
                int tileSuit = scoringSuit(tile);
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

    private static int scoringSuit(int tile) {
        if (tile == QaTaizhouTiles.JOKER) {
            return 0;
        }
        return QaTaizhouTiles.isSuited(tile) ? QaTaizhouTiles.suitOf(tile) : 0;
    }

    private static boolean terminalOrHonour(int tile) {
        return QaTaizhouTiles.isHonour(tile)
                || (QaTaizhouTiles.isSuited(tile)
                        && (QaTaizhouTiles.rankOf(tile) == 1
                                || QaTaizhouTiles.rankOf(tile) == 9));
    }

    private static int seatWind(int seat) {
        return 0x40 + Math.max(1, Math.min(4, seat));
    }

    private static boolean hasCaishen(List<Integer> concealed) {
        return concealed.contains(QaTaizhouTiles.JOKER);
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
    }
}
