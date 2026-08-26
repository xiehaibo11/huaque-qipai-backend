package com.nanbei.entertainment.backend.gameplay.application;

import java.util.ArrayList;
import java.util.List;

/**
 * 吃碰杠候选查找，按 Android {@code MahjongMeldAlgorithm}（移植自原版客户端
 * {@code BasicMahjong/Modules/GameLayer/MahAlgorithm.lua}）的同款逻辑在后端复刻。
 * 财神/替代牌换算沿用原版注释语义；QA 引擎当前不指定财神（joker=0），
 * 调用方传入 0 时换算自动失效。候选顺序即原版稳定顺序：牌在前、牌在中、牌在后。
 */
final class QaMeldCandidates {
    private QaMeldCandidates() {}

    /** 引擎下发给客户端的杠选项（kongType: EXPOSED|CONCEALED|FILL）。 */
    record KongOption(String kongType, int tileValue) {}

    /** 返回 inTile 能组成的全部吃候选，顺序与原版 findChow 一致。 */
    static List<List<Integer>> chowCandidates(
            List<Integer> handTiles, int inTile, int joker, int instead) {
        List<List<Integer>> combs = new ArrayList<>();
        int changedInTile = changeInsteadValue(inTile, joker, instead);

        int next = changeJokerValue(QaTaizhouTiles.nextOfSameSuit(changedInTile), joker, instead);
        int nextNext =
                changeJokerValue(
                        QaTaizhouTiles.nextOfSameSuit(
                                QaTaizhouTiles.nextOfSameSuit(changedInTile)),
                        joker,
                        instead);
        if (countOf(handTiles, next) > 0 && countOf(handTiles, nextNext) > 0) {
            combs.add(List.of(inTile, next, nextNext));
        }

        int previous =
                changeJokerValue(QaTaizhouTiles.previousOfSameSuit(changedInTile), joker, instead);
        if (countOf(handTiles, previous) > 0 && countOf(handTiles, next) > 0) {
            combs.add(List.of(previous, inTile, next));
        }

        int previousPrevious =
                changeJokerValue(
                        QaTaizhouTiles.previousOfSameSuit(
                                QaTaizhouTiles.previousOfSameSuit(changedInTile)),
                        joker,
                        instead);
        if (countOf(handTiles, previousPrevious) > 0 && countOf(handTiles, previous) > 0) {
            combs.add(List.of(previousPrevious, previous, inTile));
        }
        return combs;
    }

    static List<List<Integer>> chowCandidates(
            List<Integer> handTiles, int inTile, QaTaizhouJokerRule jokerRule) {
        if (jokerRule.isJoker(inTile)) {
            return List.of();
        }
        return chowCandidates(
                handTiles, inTile, jokerRule.jokerTile(), jokerRule.insteadTile());
    }

    /** 碰：手里至少两张同牌。 */
    static boolean canPung(List<Integer> handTiles, int inTile) {
        return QaTaizhouTiles.isPlayable(inTile) && countOf(handTiles, inTile) >= 2;
    }

    static boolean canPung(
            List<Integer> handTiles, int inTile, QaTaizhouJokerRule jokerRule) {
        return !jokerRule.isJoker(inTile) && canPung(handTiles, inTile);
    }

    /** 明杠（直杠）：手里正好三张同牌，加上别人打出的那张。 */
    static boolean canExposedKong(List<Integer> handTiles, int inTile) {
        return QaTaizhouTiles.isPlayable(inTile) && countOf(handTiles, inTile) == 3;
    }

    static boolean canExposedKong(
            List<Integer> handTiles, int inTile, QaTaizhouJokerRule jokerRule) {
        return !jokerRule.isJoker(inTile) && canExposedKong(handTiles, inTile);
    }

    /**
     * 自己摸牌后的暗杠与补杠选项。对齐原版短路规则以外的收集顺序：
     * 暗杠按牌值升序在前，补杠随后（Android 移植把明杠短路规则留给裁决层）。
     */
    static List<KongOption> ownDrawKongOptions(
            List<Integer> handTiles, int drawnTile, List<List<Integer>> exposedMelds) {
        List<Integer> all = new ArrayList<>(handTiles);
        List<KongOption> options = new ArrayList<>();
        List<Integer> concealedTiles = new ArrayList<>();
        for (int tile : all) {
            if (QaTaizhouTiles.isPlayable(tile)
                    && countOf(all, tile) == 4
                    && !concealedTiles.contains(tile)) {
                concealedTiles.add(tile);
            }
        }
        concealedTiles.sort(Integer::compare);
        for (int tile : concealedTiles) {
            options.add(new KongOption("CONCEALED", tile));
        }
        List<Integer> fillTiles = new ArrayList<>();
        if (exposedMelds != null) {
            for (List<Integer> meld : exposedMelds) {
                if (meld == null || meld.size() < 2 || !meld.get(0).equals(meld.get(1))) {
                    continue;
                }
                if (countOf(all, meld.get(0)) == 1 && !fillTiles.contains(meld.get(0))) {
                    fillTiles.add(meld.get(0));
                }
            }
        }
        fillTiles.sort(Integer::compare);
        for (int tile : fillTiles) {
            options.add(new KongOption("FILL", tile));
        }
        return options;
    }

    static List<KongOption> ownDrawKongOptions(
            List<Integer> handTiles,
            int drawnTile,
            List<List<Integer>> exposedMelds,
            QaTaizhouJokerRule jokerRule) {
        return ownDrawKongOptions(handTiles, drawnTile, exposedMelds).stream()
                .filter(option -> !jokerRule.isJoker(option.tileValue()))
                .toList();
    }

    static int countOf(List<Integer> tiles, int target) {
        if (target == QaTaizhouTiles.NO_TILE) {
            return 0;
        }
        int count = 0;
        for (int tile : tiles) {
            if (tile == target) {
                count++;
            }
        }
        return count;
    }

    /** 财神映射成替代牌，财神本身永不参与吃（原版注释语义）。 */
    static int changeJokerValue(int tile, int joker, int instead) {
        if (joker == QaTaizhouTiles.NO_TILE || instead == QaTaizhouTiles.NO_TILE) {
            return tile;
        }
        return tile == joker ? instead : tile;
    }

    /** 进来的替代牌映射回财神（原版注释语义）。 */
    static int changeInsteadValue(int tile, int joker, int instead) {
        if (joker == QaTaizhouTiles.NO_TILE || instead == QaTaizhouTiles.NO_TILE) {
            return tile;
        }
        return tile == instead ? joker : tile;
    }
}
