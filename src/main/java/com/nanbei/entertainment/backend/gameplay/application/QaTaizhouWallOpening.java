package com.nanbei.entertainment.backend.gameplay.application;

import java.util.List;

/** 台州麻将双骰开墙定位；座位为服务端 1-based，牌墙索引保持原协议 0-based。 */
record QaTaizhouWallOpening(
        int secondSeat,
        int openIndex,
        int firstAsc,
        int firstDesc) {
    static final int WALL_SIZE = 136;

    static QaTaizhouWallOpening fromDice(
            int dealerSeat, List<Integer> firstDice, List<Integer> secondDice) {
        if (dealerSeat < 1 || dealerSeat > 4) {
            throw new IllegalArgumentException("dealer seat is outside chair count");
        }
        validatePair(firstDice);
        validatePair(secondDice);
        int firstTotal = firstDice.get(0) + firstDice.get(1);
        int secondSeat = Math.floorMod(dealerSeat - 1 + firstTotal - 1, 4) + 1;
        int total = firstTotal + secondDice.get(0) + secondDice.get(1);
        int localOffset = 2 * Math.floorMod(17 - total, 17) + 1;
        int openIndex = (secondSeat - 1) * 34 + localOffset;
        return new QaTaizhouWallOpening(
                secondSeat,
                openIndex,
                Math.floorMod(openIndex - 2, WALL_SIZE),
                Math.floorMod(openIndex - 1, WALL_SIZE));
    }

    int ascAfterFrontDraws(int count) {
        requireDrawCount(count);
        return Math.floorMod(firstAsc - count, WALL_SIZE);
    }

    int remainingAfterFrontDraws(int count) {
        requireDrawCount(count);
        return WALL_SIZE - 1 - count;
    }

    private static void validatePair(List<Integer> dice) {
        if (dice == null || dice.size() != 2) {
            throw new IllegalArgumentException("dice must contain exactly two values");
        }
        for (int value : dice) {
            if (value < 1 || value > 6) {
                throw new IllegalArgumentException("dice value must be between 1 and 6");
            }
        }
    }

    private static void requireDrawCount(int count) {
        if (count < 0 || count > WALL_SIZE - 1) {
            throw new IllegalArgumentException("draw count is outside the opened wall");
        }
    }
}
