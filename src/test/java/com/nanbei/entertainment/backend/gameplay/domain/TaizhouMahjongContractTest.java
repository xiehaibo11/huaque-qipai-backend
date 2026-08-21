package com.nanbei.entertainment.backend.gameplay.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanbei.entertainment.backend.gameplay.taizhoumahjong.TaizhouMahjongDefinition;
import org.junit.jupiter.api.Test;

class TaizhouMahjongContractTest {
    private final GameDefinition definition = new TaizhouMahjongDefinition();

    @Test
    void identifiesOriginal30109ClientContract() {
        assertThat(definition.gameId()).isEqualTo(30109L);
        assertThat(definition.playerCounts()).containsExactly(2, 4);
    }
}
