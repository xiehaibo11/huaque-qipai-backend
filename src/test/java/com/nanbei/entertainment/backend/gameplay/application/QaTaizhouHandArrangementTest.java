package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class QaTaizhouHandArrangementTest {
    @Test
    void jokerStandsBeforeEightAndNineToCompleteASequence() {
        QaTaizhouHandArrangement.Arrangement arrangement =
                QaTaizhouHandArrangement.best(
                        List.of(
                                0x18, 0x19, QaTaizhouTiles.JOKER,
                                0x21, 0x22, 0x23,
                                0x31, 0x32, 0x33,
                                0x41, 0x41, 0x41,
                                0x52, 0x52),
                        0x41);

        assertThat(arrangement).isNotNull();
        assertThat(arrangement.sequenceCount()).isEqualTo(3);
    }

    @Test
    void choosesTheArrangementWithTheHighestFinalHuValue() {
        QaTaizhouHandArrangement.Arrangement arrangement =
                QaTaizhouHandArrangement.best(
                        List.of(
                                0x11, 0x12, 0x13,
                                0x21, 0x22, 0x23,
                                0x31, 0x32, 0x33,
                                0x42, 0x42,
                                0x51, 0x51,
                                QaTaizhouTiles.JOKER),
                        0x41);

        assertThat(arrangement).isEqualTo(new QaTaizhouHandArrangement.Arrangement(8, 1, 3));
    }
}
