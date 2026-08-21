package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class QaWinDetectorTest {
    private static final int JOKER = 0x76;

    @Test
    void detectsABasicFourMeldOnePairHand() {
        List<Integer> hand =
                List.of(
                        0x11, 0x12, 0x13,
                        0x21, 0x22, 0x23,
                        0x35, 0x35, 0x35,
                        0x41, 0x41, 0x41,
                        0x52, 0x52);

        assertThat(QaWinDetector.canWin(hand)).isTrue();
    }

    @Test
    void detectsASevenPairsStyleHandAsNotBasicWin() {
        // 南北自建 QA 胡判定只认 4 副 + 1 将基本型，非连号七对无法拆成顺子/刻子，不算胡。
        List<Integer> hand =
                List.of(
                        0x11, 0x11, 0x13, 0x13, 0x15, 0x15, 0x17,
                        0x17, 0x21, 0x21, 0x31, 0x31, 0x41, 0x41);

        assertThat(QaWinDetector.canWin(hand)).isFalse();
    }

    @Test
    void jokerStandsInForAMissingSequenceTile() {
        List<Integer> hand =
                List.of(
                        0x11, JOKER, 0x13,
                        0x21, 0x22, 0x23,
                        0x35, 0x35, 0x35,
                        0x41, 0x41, 0x41,
                        0x52, 0x52);

        assertThat(QaWinDetector.canWin(hand)).isTrue();
    }

    @Test
    void jokerStandsInForThePairAndTriplet() {
        List<Integer> pairHand =
                List.of(
                        0x11, 0x12, 0x13,
                        0x21, 0x22, 0x23,
                        0x35, 0x35, 0x35,
                        0x41, 0x41, 0x41,
                        0x52, JOKER);
        List<Integer> tripletHand =
                List.of(
                        0x11, 0x12, 0x13,
                        0x21, 0x22, 0x23,
                        0x35, 0x35, JOKER,
                        0x41, 0x41, 0x41,
                        0x52, 0x52);

        assertThat(QaWinDetector.canWin(pairHand)).isTrue();
        assertThat(QaWinDetector.canWin(tripletHand)).isTrue();
    }

    @Test
    void honoursOnlyFormTripletsNeverSequences() {
        List<Integer> hand =
                List.of(
                        0x41, 0x42, 0x43,
                        0x21, 0x22, 0x23,
                        0x35, 0x35, 0x35,
                        0x11, 0x11, 0x11,
                        0x52, 0x52);

        assertThat(QaWinDetector.canWin(hand)).isFalse();
    }

    @Test
    void rejectsAHandThatIsOneTileShort() {
        List<Integer> hand =
                List.of(
                        0x11, 0x12, 0x13,
                        0x21, 0x22, 0x23,
                        0x35, 0x35, 0x35,
                        0x41, 0x41,
                        0x52, 0x52);

        assertThat(QaWinDetector.canWin(hand)).isFalse();
    }

    @Test
    void rejectsWrongHandSizes() {
        assertThat(QaWinDetector.canWin(List.of(0x11, 0x11))).isTrue();
        assertThat(QaWinDetector.canWin(List.of(0x11, 0x12))).isFalse();
        assertThat(QaWinDetector.canWin(List.of())).isFalse();
        assertThat(QaWinDetector.canWin(List.of(0x11))).isFalse();
    }
}
