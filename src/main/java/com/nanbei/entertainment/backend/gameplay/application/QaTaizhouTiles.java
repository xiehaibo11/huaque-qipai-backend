package com.nanbei.entertainment.backend.gameplay.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * 南北自建 QA 牌值与牌墙工具，非原版服务端算法。
 *
 * <p>牌值编码沿用原版客户端 {@code GameDefine.lua} 的公开事实：{@code (suit << 4) | rank}，
 * 与 Android {@code MahjongTile} 对齐。大众玩法文档明确台州麻将为 136 张、无花牌；
 * 固定 seed 洗牌仍是南北娱乐为 QA 测试自建的确定性实现，不得把本类描述成恢复了原版算法。
 */
final class QaTaizhouTiles {
    static final int SUIT_WAN = 0x1;
    static final int SUIT_TIAO = 0x2;
    static final int SUIT_TONG = 0x3;
    static final int SUIT_FENG = 0x4;
    static final int SUIT_JIAN = 0x5;
    static final int SUIT_HUA = 0x6;
    static final int SUIT_BACK = 0x7;

    /** 原版客户端事实：花牌槽位 0x61-0x68；大众玩法牌墙不放花牌。 */
    static final int FLOWER_MIN = 0x61;
    static final int FLOWER_MAX = 0x68;

    /** 原版客户端事实：财神（joker）编码 0x76；QA 墙默认不放财神。 */
    static final int JOKER = 0x76;

    /** 原版客户端事实：牌背编码 0x72，用于对他人隐藏手牌。 */
    static final int BACK = 0x72;

    static final int NO_TILE = 0;
    static final int WALL_SIZE = 136;

    private QaTaizhouTiles() {}

    static int suitOf(int value) {
        return (value >> 4) & 0xF;
    }

    static int rankOf(int value) {
        return value & 0xF;
    }

    /** 万/条/筒数牌。 */
    static boolean isSuited(int value) {
        int suit = suitOf(value);
        return (suit == SUIT_WAN || suit == SUIT_TIAO || suit == SUIT_TONG)
                && rankOf(value) >= 1
                && rankOf(value) <= 9;
    }

    /** 风牌与箭牌。 */
    static boolean isHonour(int value) {
        int suit = suitOf(value);
        if (suit == SUIT_FENG) {
            return rankOf(value) >= 1 && rankOf(value) <= 4;
        }
        return suit == SUIT_JIAN && rankOf(value) >= 1 && rankOf(value) <= 3;
    }

    /** 数牌或字牌；花牌、牌背与财神都不进入吃碰杠候选。 */
    static boolean isPlayable(int value) {
        return isSuited(value) || isHonour(value);
    }

    /** QA 补花范围：只处理墙上的 0x61-0x68。 */
    static boolean isWallFlower(int value) {
        return value >= FLOWER_MIN && value <= FLOWER_MAX;
    }

    /** 同门前一张，不存在时返回 {@link #NO_TILE}（对齐 Android 同名移植）。 */
    static int previousOfSameSuit(int tile) {
        if (!isSuited(tile) || rankOf(tile) == 1) {
            return NO_TILE;
        }
        return tile - 1;
    }

    /** 同门后一张，不存在时返回 {@link #NO_TILE}（对齐 Android 同名移植）。 */
    static int nextOfSameSuit(int tile) {
        if (!isSuited(tile) || rankOf(tile) == 9) {
            return NO_TILE;
        }
        return tile + 1;
    }

    /**
     * 南北自建 QA 牌墙：136 张基础牌、无花牌，用请求确定性 seed 洗牌。
     */
    static List<Integer> buildWall(long seed) {
        List<Integer> wall = new ArrayList<>(WALL_SIZE);
        for (int tile : baseTiles()) {
            for (int copy = 0; copy < 4; copy++) {
                wall.add(tile);
            }
        }
        Collections.shuffle(wall, new Random(seed));
        return wall;
    }

    private static List<Integer> baseTiles() {
        List<Integer> tiles = new ArrayList<>(34);
        for (int suit : List.of(SUIT_WAN, SUIT_TIAO, SUIT_TONG)) {
            for (int rank = 1; rank <= 9; rank++) {
                tiles.add((suit << 4) + rank);
            }
        }
        for (int rank = 1; rank <= 4; rank++) {
            tiles.add((SUIT_FENG << 4) + rank);
        }
        for (int rank = 1; rank <= 3; rank++) {
            tiles.add((SUIT_JIAN << 4) + rank);
        }
        return tiles;
    }

    /** 与旧脚本引擎一致的确定性 seed（房间号 + 修订游标 + 局数 + 座位用户序列）。 */
    static long seed(
            String roomNumber, long expectedRevision, int roundNumber, List<java.util.UUID> userIds) {
        return Objects.hash(roomNumber, expectedRevision, roundNumber, userIds);
    }
}
