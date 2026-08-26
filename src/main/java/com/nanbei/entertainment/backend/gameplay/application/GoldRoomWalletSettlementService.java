package com.nanbei.entertainment.backend.gameplay.application;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionSeatEntity;
import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class GoldRoomWalletSettlementService {
    private static final int GOLD_ROOM_MODE = 50;
    private static final String GOLD_MATCH_TOKEN = "GoldMatch='1'";

    private final PlayerWalletRepository walletRepository;

    public GoldRoomWalletSettlementService(PlayerWalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    public void settle(
            GameRoomEntity room,
            List<GameSessionSeatEntity> seats,
            Map<Integer, Long> scoreDeltasBySeat) {
        if (!isProductionGoldRoom(room) || scoreDeltasBySeat.values().stream().allMatch(delta -> delta == 0L)) {
            return;
        }
        long balance = scoreDeltasBySeat.values().stream().mapToLong(Long::longValue).sum();
        if (balance != 0L) {
            throw new IllegalStateException("gold-room settlement must be zero-sum");
        }
        for (GameSessionSeatEntity seat : seats) {
            long delta = scoreDeltasBySeat.getOrDefault(seat.getId().getSeatNumber(), 0L);
            if (delta != 0L) {
                wallet(seat).applyCoinDelta(delta);
            }
        }
    }

    private PlayerWalletEntity wallet(GameSessionSeatEntity seat) {
        return walletRepository
                .findLockedByUserId(seat.getUserId())
                .orElseThrow(
                        () -> new ApiException(ErrorCode.ROOM_ILLEGAL_STATE, "金币场玩家钱包不存在"));
    }

    /**
     * 只认生产金币场。规则串是 {@code key='value'} 的分号列表，必须整段比对：
     * QA 场写的是 {@code QaGoldMatch='1'}，用 contains 会把它也当成生产场，
     * 于是 QA 玩家没有钱包时整条胡牌命令都被打回。
     */
    private static boolean isProductionGoldRoom(GameRoomEntity room) {
        if (room.getRoomMode() != GOLD_ROOM_MODE || room.getGameRule() == null) {
            return false;
        }
        for (String token : room.getGameRule().split(";")) {
            if (GOLD_MATCH_TOKEN.equals(token.trim())) {
                return true;
            }
        }
        return false;
    }
}
