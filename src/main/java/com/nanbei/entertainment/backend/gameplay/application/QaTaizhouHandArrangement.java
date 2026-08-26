package com.nanbei.entertainment.backend.gameplay.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Chooses the highest scoring N-melds-plus-pair decomposition for a concealed hand. */
final class QaTaizhouHandArrangement {
    private QaTaizhouHandArrangement() {}

    record Arrangement(int points, int fan, int sequenceCount) {
        Arrangement withAdded(int addPoints, int addFan, int addSequences) {
            return new Arrangement(
                    points + addPoints, fan + addFan, sequenceCount + addSequences);
        }
    }

    static Arrangement best(List<Integer> concealed, int seatWind) {
        int jokers = 0;
        Map<Integer, Integer> counts = new TreeMap<>();
        for (int tile : concealed) {
            if (tile == QaTaizhouTiles.JOKER) {
                jokers++;
            } else if (QaTaizhouTiles.isPlayable(tile)) {
                counts.merge(tile, 1, Integer::sum);
            } else {
                return null;
            }
        }
        if ((counts.values().stream().mapToInt(Integer::intValue).sum() + jokers) % 3 != 2) {
            return null;
        }
        Arrangement best = null;
        for (int pairTile : allPlayableTiles()) {
            int available = counts.getOrDefault(pairTile, 0);
            int realUsed = Math.min(2, available);
            for (int used = realUsed; used >= 0; used--) {
                int jokerNeeded = 2 - used;
                if (jokerNeeded > jokers || used == 0 && jokerNeeded == 0) {
                    continue;
                }
                Arrangement melds =
                        bestMelds(remove(counts, pairTile, used), jokers - jokerNeeded, seatWind);
                if (melds != null) {
                    best =
                            better(
                                    best,
                                    melds.withAdded(
                                            QaTaizhouScorer.pairPoint(pairTile, seatWind), 0, 0));
                }
            }
        }
        return best;
    }

    private static Arrangement bestMelds(Map<Integer, Integer> counts, int jokers, int seatWind) {
        return bestMelds(counts, jokers, seatWind, new LinkedHashMap<>());
    }

    private static Arrangement bestMelds(
            Map<Integer, Integer> counts,
            int jokers,
            int seatWind,
            Map<String, Arrangement> memo) {
        if (counts.isEmpty()) {
            return jokers % 3 == 0 ? new Arrangement(0, 0, 0) : null;
        }
        String key = counts + "|" + jokers;
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        int first = counts.keySet().iterator().next();
        Arrangement best = tryTriplet(counts, jokers, seatWind, memo, first);
        best = better(best, trySequence(counts, jokers, seatWind, memo, first));
        memo.put(key, best);
        return best;
    }

    private static Arrangement tryTriplet(
            Map<Integer, Integer> counts,
            int jokers,
            int seatWind,
            Map<String, Arrangement> memo,
            int first) {
        int tripletUsed = Math.min(3, counts.get(first));
        int jokerNeeded = 3 - tripletUsed;
        if (jokerNeeded > jokers) {
            return null;
        }
        Arrangement rest =
                bestMelds(remove(counts, first, tripletUsed), jokers - jokerNeeded, seatWind, memo);
        if (rest == null) {
            return null;
        }
        return rest.withAdded(
                QaTaizhouScorer.tripletPoint(first, true),
                QaTaizhouScorer.fanForTriplet(first, seatWind),
                0);
    }

    private static Arrangement trySequence(
            Map<Integer, Integer> counts,
            int jokers,
            int seatWind,
            Map<String, Arrangement> memo,
            int first) {
        if (!QaTaizhouTiles.isSuited(first)) {
            return null;
        }
        Arrangement best = null;
        int rank = QaTaizhouTiles.rankOf(first);
        for (int startRank = Math.max(1, rank - 2); startRank <= Math.min(7, rank); startRank++) {
            int start = (QaTaizhouTiles.suitOf(first) << 4) + startRank;
            for (int jokerMask = 0; jokerMask < 8; jokerMask++) {
                Map<Integer, Integer> next = new TreeMap<>(counts);
                int usedJokers = 0;
                boolean valid = true;
                for (int offset = 0; offset < 3; offset++) {
                    int tile = start + offset;
                    boolean useJoker = tile != first && (jokerMask & (1 << offset)) != 0;
                    if (useJoker) {
                        usedJokers++;
                    } else if (next.getOrDefault(tile, 0) > 0) {
                        next = remove(next, tile, 1);
                    } else {
                        valid = false;
                        break;
                    }
                }
                if (!valid || usedJokers > jokers) {
                    continue;
                }
                Arrangement rest = bestMelds(next, jokers - usedJokers, seatWind, memo);
                if (rest != null) {
                    best = better(best, rest.withAdded(0, 0, 1));
                }
            }
        }
        return best;
    }

    private static Arrangement better(Arrangement current, Arrangement candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        int candidateRank = finalHu(candidate);
        int currentRank = finalHu(current);
        return candidateRank > currentRank ? candidate : current;
    }

    private static int finalHu(Arrangement arrangement) {
        long total = 10L + arrangement.points();
        for (int index = 0; index < arrangement.fan() && total < 100; index++) {
            total *= 2L;
        }
        return (int) Math.min(100, total);
    }

    private static Map<Integer, Integer> remove(
            Map<Integer, Integer> counts, int tile, int amount) {
        if (amount <= 0) {
            return counts;
        }
        Map<Integer, Integer> next = new TreeMap<>(counts);
        int left = next.getOrDefault(tile, 0) - amount;
        if (left <= 0) {
            next.remove(tile);
        } else {
            next.put(tile, left);
        }
        return next;
    }

    private static List<Integer> allPlayableTiles() {
        List<Integer> tiles = new ArrayList<>(34);
        for (int suit :
                List.of(
                        QaTaizhouTiles.SUIT_WAN,
                        QaTaizhouTiles.SUIT_TIAO,
                        QaTaizhouTiles.SUIT_TONG)) {
            for (int rank = 1; rank <= 9; rank++) {
                tiles.add((suit << 4) + rank);
            }
        }
        for (int rank = 1; rank <= 4; rank++) {
            tiles.add((QaTaizhouTiles.SUIT_FENG << 4) + rank);
        }
        for (int rank = 1; rank <= 3; rank++) {
            tiles.add((QaTaizhouTiles.SUIT_JIAN << 4) + rank);
        }
        return tiles;
    }
}
