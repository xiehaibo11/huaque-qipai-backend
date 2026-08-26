package com.nanbei.entertainment.backend.gameplay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionSeatEntity;
import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GoldRoomWalletSettlementServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID WINNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID LOSER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void appliesAuthoritativeRoundDeltasToRealGoldWallets() {
        PlayerWalletRepository wallets = mock(PlayerWalletRepository.class);
        PlayerWalletEntity winner = new PlayerWalletEntity(WINNER_ID, 0, 0, 10_000, 0);
        PlayerWalletEntity loser = new PlayerWalletEntity(LOSER_ID, 0, 0, 10_000, 0);
        when(wallets.findLockedByUserId(WINNER_ID)).thenReturn(Optional.of(winner));
        when(wallets.findLockedByUserId(LOSER_ID)).thenReturn(Optional.of(loser));
        GameRoomEntity room = mock(GameRoomEntity.class);
        when(room.getRoomMode()).thenReturn(50);
        when(room.getGameRule()).thenReturn("GoldMatch='1';");
        List<GameSessionSeatEntity> seats =
                List.of(
                        new GameSessionSeatEntity(SESSION_ID, 1, WINNER_ID, 10_000, NOW),
                        new GameSessionSeatEntity(SESSION_ID, 2, LOSER_ID, 10_000, NOW));

        new GoldRoomWalletSettlementService(wallets)
                .settle(room, seats, Map.of(1, 160L, 2, -160L));

        assertThat(winner.getCoins()).isEqualTo(10_160L);
        assertThat(loser.getCoins()).isEqualTo(9_840L);
        verify(wallets).findLockedByUserId(WINNER_ID);
        verify(wallets).findLockedByUserId(LOSER_ID);
    }

    @Test
    void skipsQaGoldRoomsWhoseRuleOnlyContainsTheProductionTokenAsASuffix() {
        PlayerWalletRepository wallets = mock(PlayerWalletRepository.class);
        GameRoomEntity room = mock(GameRoomEntity.class);
        when(room.getRoomMode()).thenReturn(50);
        when(room.getGameRule()).thenReturn("PayType='0';autoReady='1';QaGoldMatch='1';");
        List<GameSessionSeatEntity> seats =
                List.of(
                        new GameSessionSeatEntity(SESSION_ID, 1, WINNER_ID, 10_000, NOW),
                        new GameSessionSeatEntity(SESSION_ID, 2, LOSER_ID, 10_000, NOW));

        new GoldRoomWalletSettlementService(wallets)
                .settle(room, seats, Map.of(1, 160L, 2, -160L));

        verifyNoInteractions(wallets);
    }
}
