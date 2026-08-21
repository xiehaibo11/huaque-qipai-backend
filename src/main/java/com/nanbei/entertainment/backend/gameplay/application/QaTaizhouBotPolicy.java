package com.nanbei.entertainment.backend.gameplay.application;

import java.util.List;
import java.util.Objects;

/**
 * 南北自建 QA 假人策略，非原版服务端算法：
 * 不吃、不碰、不杠，只在自建胡判定通过时胡；出牌固定打手牌最大值（沿用旧脚本引擎风格）。
 */
final class QaTaizhouBotPolicy {
    /** 摸牌后的假人决策：能胡则胡，否则打最大牌。 */
    boolean wantsSelfDrawWin(List<Integer> hand) {
        return QaWinDetector.canWin(hand);
    }

    /** 对他人弃牌的假人决策：只在能胡时声明胡。 */
    boolean wantsClaimWin(List<Integer> hand, int inTile) {
        if (!QaTaizhouTiles.isPlayable(inTile)) {
            return false;
        }
        return QaWinDetector.canWin(append(hand, inTile));
    }

    /** 假人出牌：打手牌中数值最大的一张（花牌与财神不进手牌）。 */
    int discardChoice(List<Integer> hand) {
        if (hand.isEmpty()) {
            throw new IllegalStateException("cannot discard from an empty hand");
        }
        int choice = hand.get(0);
        for (int tile : hand) {
            if (tile > choice) {
                choice = tile;
            }
        }
        return choice;
    }

    /** 客户端播放用拟人化思考时长；不阻塞后端事务。 */
    static long thinkingDelayMillis(QaRoundTable table) {
        int offset =
                Math.floorMod(
                        Objects.hash(
                                table.roundNumber,
                                table.turnIndex,
                                table.activeSeat,
                                table.wall.size(),
                                table.rivers().get(table.activeSeat).size()),
                        401);
        return 700L + offset;
    }

    private static List<Integer> append(List<Integer> hand, int tile) {
        java.util.ArrayList<Integer> all = new java.util.ArrayList<>(hand);
        all.add(tile);
        return all;
    }
}
