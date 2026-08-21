package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class QaMeldCandidatesTest {
    private static final int NO_TILE = 0;

    @Test
    void chowCandidatesFollowTheOriginalStableOrderTileFirstMiddleLast() {
        List<List<Integer>> candidates =
                QaMeldCandidates.chowCandidates(
                        List.of(0x12, 0x13, 0x14, 0x15, 0x16, 0x39), 0x14, NO_TILE, NO_TILE);

        assertThat(candidates)
                .containsExactly(
                        List.of(0x14, 0x15, 0x16),
                        List.of(0x13, 0x14, 0x15),
                        List.of(0x12, 0x13, 0x14));
    }

    @Test
    void chowRejectsHonoursAndSuitBoundaries() {
        // 字牌不能吃。
        assertThat(
                        QaMeldCandidates.chowCandidates(
                                List.of(0x42, 0x43), 0x41, NO_TILE, NO_TILE))
                .isEmpty();
        // 缺 0x23 时 0x21 无法组成顺子；0x28/0x29 与 0x21 不同端点也不成顺。
        assertThat(
                        QaMeldCandidates.chowCandidates(
                                List.of(0x21, 0x22, 0x28, 0x29), 0x21, NO_TILE, NO_TILE))
                .isEmpty();
        // 9 不能作为顺子起点继续向上。
        assertThat(
                        QaMeldCandidates.chowCandidates(
                                List.of(0x31, 0x32), 0x39, NO_TILE, NO_TILE))
                .isEmpty();
    }

    @Test
    void chowMapsAnIncomingInsteadTileOntoTheJokerLikeTheOriginalComment() {
        // joker=0x13 stands in for instead=0x53: an incoming 0x53 is evaluated as 0x13.
        List<List<Integer>> candidates =
                QaMeldCandidates.chowCandidates(List.of(0x11, 0x12, 0x39), 0x53, 0x13, 0x53);

        assertThat(candidates).containsExactly(List.of(0x11, 0x12, 0x53));
    }

    @Test
    void pungRequiresTwoCopiesInHand() {
        assertThat(QaMeldCandidates.canPung(List.of(0x25, 0x25, 0x31), 0x25)).isTrue();
        assertThat(QaMeldCandidates.canPung(List.of(0x25, 0x31), 0x25)).isFalse();
        assertThat(QaMeldCandidates.canPung(List.of(0x25, 0x25), 0x61)).isFalse();
    }

    @Test
    void exposedKongRequiresThreeCopiesInHand() {
        assertThat(QaMeldCandidates.canExposedKong(List.of(0x37, 0x37, 0x37), 0x37)).isTrue();
        assertThat(QaMeldCandidates.canExposedKong(List.of(0x37, 0x37), 0x37)).isFalse();
    }

    @Test
    void concealedAndFillKongsAreCollectedOnThePlayersOwnDraw() {
        List<QaMeldCandidates.KongOption> options =
                QaMeldCandidates.ownDrawKongOptions(
                        List.of(0x11, 0x11, 0x11, 0x11, 0x22, 0x23, 0x24, 0x35),
                        0x35,
                        List.of(List.of(0x35, 0x35, 0x35)));

        assertThat(options)
                .containsExactly(
                        new QaMeldCandidates.KongOption("CONCEALED", 0x11),
                        new QaMeldCandidates.KongOption("FILL", 0x35));
    }

    @Test
    void fillKongNeedsAnExistingPongAndOneMoreCopyInHand() {
        assertThat(
                        QaMeldCandidates.ownDrawKongOptions(
                                List.of(0x22, 0x23, 0x24), 0x35, List.of(List.of(0x35, 0x35, 0x35))))
                .isEmpty();
    }
}
