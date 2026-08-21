package com.nanbei.entertainment.backend.gamehome.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerWalletRoomCardCentiTest {
    @Test
    void debitsFractionalRoomCardsInCentiWithoutRounding() {
        PlayerWalletEntity wallet = new PlayerWalletEntity(UUID.randomUUID(), 2, 0, 0, 0);

        wallet.debitRoomCardCenti(25);

        assertThat(wallet.getRoomCardCenti()).isEqualTo(175);
        assertThat(wallet.getRoomCards()).isEqualTo(1);
    }

    @Test
    void refusesACentiDebitAboveTheBalance() {
        PlayerWalletEntity wallet = new PlayerWalletEntity(UUID.randomUUID(), 1, 0, 0, 0);

        assertThatThrownBy(() -> wallet.debitRoomCardCenti(150))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("insufficient");
        assertThat(wallet.getRoomCardCenti()).isEqualTo(100);
    }
}
