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

    static Arrangement best(List<Integer> concealed, int seat) {
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
                        bestMelds(remove(counts, pairTile, used), jokers - jokerNeeded, seat);
                if (melds != null) {
                    best =
                            better(
                                    best,
                                    melds.withAdded(
                                            QaTaizhouScorer.pairPoint(pairTile, seat), 0, 0));
                }
            }
        }
        return best;
    }

    private static Arrangement bestMelds(Map<Integer, Integer> counts, int jokers, int seat) {
        return bestMelds(counts, jokers, seat, new LinkedHashMap<>());
    }

    private static Arrangement bestMelds(
            Map<Integer, Integer> counts,
            int jokers,
            int seat,
            Map<String, Arrangement> memo) {
        if (counts.isEmpty()) {
            return jokers % 3 == 0 ? new Arrangement(0, 0, 0) : null;
        }
        String key = counts + "|" + jokers;
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        int first = counts.keySet().iterator().next();
        Arrangement best = tryTriplet(counts, jokers, seat, memo, first);
        best = better(best, trySequence(counts, jokers, seat, memo, first));
        memo.put(key, best);
        return best;
    }

    private static Arrangement tryTriplet(
            Map<Integer, Integer> counts,
            int jokers,
            int seat,
            Map<String, Arrangement> memo,
            int first) {
        int tripletUsed = Math.min(3, counts.get(first));
        int jokerNeeded = 3 - tripletUsed;
        if (jokerNeeded > jokers) {
            return null;
        }
        Arrangement rest =
                bestMelds(remove(counts, first, tripletUsed), jokers - jokerNeeded, seat, memo);
        if (rest == null) {
            return null;
        }
        return rest.withAdded(
                QaTaizhouScorer.tripletPoint(first, true),
                QaTaizhouScorer.fanForTriplet(first, seat),
                0);
    }

    private static Arrangement trySequence(
            Map<Integer, Integer> counts,
            int jokers,
            int seat,
            Map<String, Arrangement> memo,
            int first) {
        if (!QaTaizhouTiles.isSuited(first) || QaTaizhouTiles.rankOf(first) > 7) {
            return null;
        }
        int second = first + 1;
        int third = first + 2;
        int secondUsed = counts.getOrDefault(second, 0) > 0 ? 1 : 0;
        int thirdUsed = counts.getOrDefault(third, 0) > 0 ? 1 : 0;
        int sequenceJokers = 2 - secondUsed - thirdUsed;
        if (sequenceJokers > jokers) {
            return null;
        }
        Map<Integer, Integer> next = remove(counts, first, 1);
        next = remove(next, second, secondUsed);
        next = remove(next, third, thirdUsed);
        Arrangement rest = bestMelds(next, jokers - sequenceJokers, seat, memo);
        return rest == null ? null : rest.withAdded(0, 0, 1);
    }

    private static Arrangement better(Arrangement current, Arrangement candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        int candidateRank = candidate.points() * 32 + candidate.fan();
        int currentRank = current.points() * 32 + current.fan();
        return candidateRank > currentRank ? candidate : current;
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
