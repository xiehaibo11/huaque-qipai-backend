package com.nanbei.entertainment.backend.gameplay.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class QaTaizhouBaoPai {
    private QaTaizhouBaoPai() {}

    static QaRoundTable.DiscardSnapshot beforeDiscard(
            QaRoundTable table, int seat, int tile, List<Integer> hand) {
        boolean rawTile = !table.discardedTileTypes.contains(tile);
        boolean allHandRaw =
                hand.stream().allMatch(value -> !table.discardedTileTypes.contains(value));
        return new QaRoundTable.DiscardSnapshot(
                seat, tile, table.shengPaiCount >= 0, rawTile, allHandRaw);
    }

    static Integer contractor(
            QaRoundTable table,
            int winnerSeat,
            String winType,
            Integer discarderSeat,
            int winningTile,
            QaTaizhouScorer.SeatScore winnerScore) {
        if (!enabled(table)) {
            return null;
        }
        QaRoundTable.DiscardSnapshot snapshot = table.lastDiscardSnapshot;
        if ("DIANPAO".equals(winType)
                && discarderSeat != null
                && snapshot != null
                && snapshot.seat() == discarderSeat
                && snapshot.tile() == winningTile
                && snapshot.shengPaiStage()
                && snapshot.rawTile()
                && !snapshot.allHandRaw()) {
            return discarderSeat;
        }
        if (!winnerScore.fanNames().contains("清一色")) {
            return null;
        }
        int suit = winningSuit(table, winnerSeat, winningTile);
        if (suit == 0) {
            return null;
        }
        if ("ZIMO".equals(winType)) {
            return threeMeldProvider(table, winnerSeat, suit);
        }
        return "DIANPAO".equals(winType)
                        && discarderSeat != null
                        && tileSuit(table, winningTile) == suit
                        && sameSuitMeldCount(table, winnerSeat, suit, null) >= 3
                ? discarderSeat
                : null;
    }

    static List<Integer> preBaoOriginalIndexes(QaRoundTable table, int seat) {
        if (!enabled(table)) {
            return List.of();
        }
        List<Integer> hand = table.hands().get(seat);
        boolean allHandRaw =
                hand.stream().allMatch(tile -> !table.discardedTileTypes.contains(tile));
        List<Integer> indexes = new ArrayList<>();
        if (table.hasDrawnTile(seat)
                && warnsBeforeDiscard(table, seat, table.drawnTile, allHandRaw)) {
            indexes.add(0);
        }
        List<Integer> concealed = QaTaizhouProjection.concealedTiles(table, seat, hand);
        for (int index = 0; index < concealed.size(); index++) {
            if (warnsBeforeDiscard(table, seat, concealed.get(index), allHandRaw)) {
                indexes.add(index + 1);
            }
        }
        return List.copyOf(indexes);
    }

    static Map<Integer, Long> reassign(Map<Integer, Long> rawDeltas, int contractorSeat) {
        Map<Integer, Long> reassigned = new LinkedHashMap<>();
        long otherTotal = 0L;
        for (Map.Entry<Integer, Long> entry : rawDeltas.entrySet()) {
            int seat = entry.getKey();
            long delta = seat != contractorSeat && entry.getValue() < 0L ? 0L : entry.getValue();
            if (seat != contractorSeat) {
                otherTotal = Math.addExact(otherTotal, delta);
            }
            reassigned.put(seat, delta);
        }
        reassigned.put(contractorSeat, Math.negateExact(otherTotal));
        return reassigned;
    }

    static Map<String, Boolean> flagsBySeat(QaRoundTable table) {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        for (int seat = 1; seat <= table.chairCount; seat++) {
            flags.put(Integer.toString(seat), false);
        }
        if (!enabled(table)) {
            return flags;
        }
        if (table.baoPaiSeat != null) {
            flags.put(Integer.toString(table.baoPaiSeat), true);
        }
        for (int winner = 1; winner <= table.chairCount; winner++) {
            for (int provider = 1; provider <= table.chairCount; provider++) {
                if (provider == winner) {
                    continue;
                }
                for (int suit = 1; suit <= 3; suit++) {
                    if (sameSuitMeldCount(table, winner, suit, provider) >= 3) {
                        flags.put(Integer.toString(provider), true);
                    }
                }
            }
        }
        return flags;
    }

    private static boolean enabled(QaRoundTable table) {
        return table.goldMode && table.chairCount == 4;
    }

    private static boolean warnsBeforeDiscard(
            QaRoundTable table, int discarder, int tile, boolean allHandRaw) {
        boolean shengPaiBao =
                table.shengPaiCount >= 0
                        && !allHandRaw
                        && !table.discardedTileTypes.contains(tile);
        return shengPaiBao || completesThreeMeldPureSuit(table, discarder, tile);
    }

    private static boolean completesThreeMeldPureSuit(
            QaRoundTable table, int discarder, int tile) {
        int suit = tileSuit(table, tile);
        if (suit == 0 || allTilesInSuit(table, table.hands().get(discarder), suit)) {
            return false;
        }
        for (int winner = 1; winner <= table.chairCount; winner++) {
            if (winner == discarder
                    || sameSuitMeldCount(table, winner, suit, null) < 3
                    || !allTilesInSuit(table, table.hands().get(winner), suit)) {
                continue;
            }
            List<Integer> winningHand = new ArrayList<>(table.hands().get(winner));
            winningHand.add(tile);
            if (QaWinDetector.canWin(winningHand, table.jokerRule)) {
                return true;
            }
        }
        return false;
    }

    private static boolean allTilesInSuit(
            QaRoundTable table, List<Integer> tiles, int suit) {
        for (int tile : tiles) {
            if (!table.jokerRule.isJoker(tile) && tileSuit(table, tile) != suit) {
                return false;
            }
        }
        return true;
    }

    private static Integer threeMeldProvider(QaRoundTable table, int winnerSeat, int suit) {
        for (int provider = 1; provider <= table.chairCount; provider++) {
            if (provider != winnerSeat
                    && sameSuitMeldCount(table, winnerSeat, suit, provider) >= 3) {
                return provider;
            }
        }
        return null;
    }

    private static int sameSuitMeldCount(
            QaRoundTable table, int winnerSeat, int suit, Integer provider) {
        int count = 0;
        for (QaRoundTable.Meld meld : table.melds().get(winnerSeat)) {
            if ((provider == null || meld.fromSeat() == provider) && meldSuit(table, meld) == suit) {
                count++;
            }
        }
        return count;
    }

    private static int winningSuit(QaRoundTable table, int winnerSeat, int winningTile) {
        int suit = tileSuit(table, winningTile);
        if (suit != 0) {
            return suit;
        }
        for (int tile : table.hands().get(winnerSeat)) {
            suit = tileSuit(table, tile);
            if (suit != 0) {
                return suit;
            }
        }
        return 0;
    }

    private static int meldSuit(QaRoundTable table, QaRoundTable.Meld meld) {
        int suit = 0;
        for (int tile : meld.tiles()) {
            int current = tileSuit(table, tile);
            if (current == 0) {
                return 0;
            }
            if (suit != 0 && current != suit) {
                return 0;
            }
            suit = current;
        }
        return suit;
    }

    private static int tileSuit(QaRoundTable table, int tile) {
        if (table.jokerRule.isJoker(tile)) {
            return 0;
        }
        int ordinary = table.jokerRule.normalizedOrdinaryTile(tile);
        return QaTaizhouTiles.isSuited(ordinary) ? QaTaizhouTiles.suitOf(ordinary) : 0;
    }
}
