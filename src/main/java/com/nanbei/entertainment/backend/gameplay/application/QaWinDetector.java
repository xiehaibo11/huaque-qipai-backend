package com.nanbei.entertainment.backend.gameplay.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 南北自建 QA 胡判定，非原版服务端算法。
 *
 * <p>只认 N 副 + 1 将的基本型；财神（0x76）作为万能牌参与替换，三张财神自成一个刻子、
 * 两张财神自成将。台州腊子胡、承包、胡型计番都不在本判定内（仓库无原版服务端证据），
 * 假人永远按本判定行事，真人 HU 命令也必须通过本判定才被接受。七对等扩展胡型不属于
 * QA 范围。
 */
final class QaWinDetector {
    private QaWinDetector() {}

    /** 判断 conceal 手牌（含财神万能牌）能否组成 N 副 + 1 将。 */
    static boolean canWin(List<Integer> concealedTiles) {
        if (concealedTiles == null || concealedTiles.isEmpty()
                || concealedTiles.size() % 3 != 2) {
            return false;
        }
        int jokers = 0;
        Map<Integer, Integer> counts = new TreeMap<>();
        for (int tile : concealedTiles) {
            if (tile == QaTaizhouTiles.JOKER) {
                jokers++;
            } else if (QaTaizhouTiles.isPlayable(tile)) {
                counts.merge(tile, 1, Integer::sum);
            } else {
                return false;
            }
        }
        // 先选将（对子），余牌必须全部成副，避免多对子被误判为胡。
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            int tile = entry.getKey();
            int count = entry.getValue();
            if (count >= 2 && allMelds(without(counts, tile, 2), jokers, new HashMap<>())) {
                return true;
            }
            if (count == 1
                    && jokers >= 1
                    && allMelds(without(counts, tile, 1), jokers - 1, new HashMap<>())) {
                return true;
            }
        }
        return jokers >= 2 && allMelds(counts, jokers - 2, new HashMap<>());
    }

    /** 余牌全部成副（刻子/顺子，财神补缺）；剩余财神按 3 张一副计。 */
    private static boolean allMelds(
            Map<Integer, Integer> counts, int jokers, Map<String, Boolean> memo) {
        int remaining = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (remaining == 0) {
            return jokers % 3 == 0;
        }
        String key = counts + "|" + jokers;
        Boolean cached = memo.get(key);
        if (cached != null) {
            return cached;
        }
        boolean result = tryTriplet(counts, jokers, memo) || trySequence(counts, jokers, memo);
        memo.put(key, result);
        return result;
    }

    /** 最小牌放进刻子：缺少的张数由财神补齐。 */
    private static boolean tryTriplet(
            Map<Integer, Integer> counts, int jokers, Map<String, Boolean> memo) {
        int first = counts.keySet().iterator().next();
        int firstCount = counts.get(first);
        for (int need = Math.max(0, 3 - firstCount); need <= Math.min(jokers, 2); need++) {
            if (allMelds(without(counts, first, 3 - need), jokers - need, memo)) {
                return true;
            }
        }
        return false;
    }

    /** 最小牌放进顺子（仅数牌）：缺位由财神补齐。 */
    private static boolean trySequence(
            Map<Integer, Integer> counts, int jokers, Map<String, Boolean> memo) {
        int first = counts.keySet().iterator().next();
        if (!QaTaizhouTiles.isSuited(first) || QaTaizhouTiles.rankOf(first) > 7) {
            return false;
        }
        int second = first + 1;
        int third = first + 2;
        int secondCount = counts.getOrDefault(second, 0);
        int thirdCount = counts.getOrDefault(third, 0);
        int need = (secondCount > 0 ? 0 : 1) + (thirdCount > 0 ? 0 : 1);
        if (jokers < need) {
            return false;
        }
        Map<Integer, Integer> next = without(counts, first, 1);
        next = without(next, second, Math.min(1, secondCount));
        next = without(next, third, Math.min(1, thirdCount));
        return allMelds(next, jokers - need, memo);
    }

    private static Map<Integer, Integer> without(Map<Integer, Integer> counts, int tile, int amount) {
        Map<Integer, Integer> next = new TreeMap<>(counts);
        if (amount <= 0) {
            return next;
        }
        int left = next.getOrDefault(tile, 0) - amount;
        if (left <= 0) {
            next.remove(tile);
        } else {
            next.put(tile, left);
        }
        return next;
    }
}
