package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 假人出牌节奏的分布形状：应当像真人，而不是均匀随机。 */
class QaBotThinkingRhythmTest {

    private static QaRoundTable table(
            int roundNumber, int turnIndex, int seat, int wallSize, List<Integer> hand,
            int discardsSoFar) {
        QaRoundTable table =
                QaRoundTable.newRound(4, 1, roundNumber, List.of(1, 2, 3, 4));
        table.turnIndex = turnIndex;
        table.activeSeat = seat;
        table.wall.clear();
        for (int i = 0; i < wallSize; i++) {
            table.wall.add(QaTaizhouTiles.SUIT_WAN << 4 | 1);
        }
        table.hands().get(seat).clear();
        table.hands().get(seat).addAll(hand);
        table.rivers().get(seat).clear();
        for (int i = 0; i < discardsSoFar; i++) {
            table.rivers().get(seat).add(QaTaizhouTiles.SUIT_WAN << 4 | 1);
        }
        return table;
    }

    private static List<Integer> hand(int kinds) {
        List<Integer> hand = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            hand.add(QaTaizhouTiles.SUIT_WAN << 4 | (1 + (i % Math.max(1, kinds))));
        }
        return hand;
    }

    private static List<Long> sample() {
        List<Long> samples = new ArrayList<>();
        for (int round = 1; round <= 8; round++) {
            for (int turn = 0; turn < 60; turn++) {
                for (int seat = 1; seat <= 4; seat++) {
                    int wall = Math.max(17, 83 - turn);
                    samples.add(
                            QaBotThinkingRhythm.thinkingDelayMillis(
                                    table(round, turn, seat, wall, hand(9), turn)));
                }
            }
        }
        return samples;
    }

    @Test
    void everySampleStaysInsideTheEightToFifteenSecondPlaybackWindow() {
        assertThat(sample()).allSatisfy(delay -> assertThat(delay).isBetween(8_000L, 15_000L));
    }

    @Test
    void sampleDelaysDoNotDependOnThePlaybackClamp() {
        assertThat(sample())
                .allSatisfy(
                        delay ->
                                assertThat(delay)
                                        .isGreaterThan(QaBotThinkingRhythm.MIN_MILLIS)
                                        .isLessThan(QaBotThinkingRhythm.MAX_MILLIS));
    }

    @Test
    void theOpeningDiscardIsSlowerThanALateOne() {
        long opening =
                QaBotThinkingRhythm.thinkingDelayMillis(table(1, 0, 1, 83, hand(11), 0));
        long late =
                QaBotThinkingRhythm.thinkingDelayMillis(table(1, 40, 1, 25, hand(5), 12));

        assertThat(opening).isGreaterThan(late);
    }

    @Test
    void aScatteredHandTakesLongerThanASettledOne() {
        long scattered =
                QaBotThinkingRhythm.thinkingDelayMillis(table(3, 10, 2, 60, hand(13), 4));
        long settled =
                QaBotThinkingRhythm.thinkingDelayMillis(table(3, 10, 2, 60, hand(4), 4));

        assertThat(scattered).isGreaterThan(settled);
    }

    @Test
    void theSameTurnAlwaysReplaysAtTheSameSpeed() {
        long first = QaBotThinkingRhythm.thinkingDelayMillis(table(2, 7, 3, 55, hand(8), 3));
        long second = QaBotThinkingRhythm.thinkingDelayMillis(table(2, 7, 3, 55, hand(8), 3));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void consecutiveTurnsDoNotMarchInLockstep() {
        List<Long> run = new ArrayList<>();
        for (int turn = 0; turn < 24; turn++) {
            run.add(QaBotThinkingRhythm.thinkingDelayMillis(table(1, turn, 1, 70, hand(9), 5)));
        }

        // 相邻回合的差值应当有正有负，而不是单调滑动（旧的 Objects.hash 会呈条带）。
        long increases = 0;
        long decreases = 0;
        for (int i = 1; i < run.size(); i++) {
            if (run.get(i) > run.get(i - 1)) {
                increases++;
            } else if (run.get(i) < run.get(i - 1)) {
                decreases++;
            }
        }
        assertThat(increases).isGreaterThan(4);
        assertThat(decreases).isGreaterThan(4);
    }
}
