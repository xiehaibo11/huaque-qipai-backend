package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class QaAdjudicatorTest {
    @Test
    void huBeatsKongWhichBeatsPungWhichBeatsChow() {
        assertThat(
                        QaAdjudicator.choose(
                                        List.of(
                                                new QaClaim(2, QaClaim.Kind.CHOW),
                                                new QaClaim(3, QaClaim.Kind.PUNG)),
                                        1,
                                        4)
                                .kind())
                .isEqualTo(QaClaim.Kind.PUNG);
        assertThat(
                        QaAdjudicator.choose(
                                        List.of(
                                                new QaClaim(2, QaClaim.Kind.PUNG),
                                                new QaClaim(4, QaClaim.Kind.KONG)),
                                        1,
                                        4)
                                .kind())
                .isEqualTo(QaClaim.Kind.KONG);
        assertThat(
                        QaAdjudicator.choose(
                                        List.of(
                                                new QaClaim(2, QaClaim.Kind.KONG),
                                                new QaClaim(3, QaClaim.Kind.HU)),
                                        1,
                                        4)
                                .kind())
                .isEqualTo(QaClaim.Kind.HU);
    }

    @Test
    void tiedHuClaimsResolveToTheNearestSeatClockwiseFromTheDiscarder() {
        QaClaim winner =
                QaAdjudicator.choose(
                        List.of(new QaClaim(1, QaClaim.Kind.HU), new QaClaim(3, QaClaim.Kind.HU)),
                        2,
                        4);

        assertThat(winner.seat()).isEqualTo(3);
    }

    @Test
    void anEmptyClaimListHasNoWinner() {
        assertThat(QaAdjudicator.choose(List.of(), 1, 4)).isNull();
    }
}
