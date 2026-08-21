package com.nanbei.entertainment.backend.timeloginact.application;

import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.shop.application.ShopWalletResponse;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 定时登录奖励的权威钱包入账，复用大厅既有 {@code player_wallets}。 */
@Component
public class TimeLoginWallets {
    private final PlayerWalletRepository repository;

    public TimeLoginWallets(PlayerWalletRepository repository) {
        this.repository = repository;
    }

    public PlayerWalletEntity locked(UUID userId) {
        return repository
                .findLockedByUserId(userId)
                .orElseGet(() -> repository.save(new PlayerWalletEntity(userId, 0, 0, 0, 0)));
    }

    public ShopWalletResponse view(UUID userId) {
        return repository
                .findById(userId)
                .map(ShopWalletResponse::from)
                .orElseGet(() -> new ShopWalletResponse(0, 0, 0, 0));
    }

    public ShopWalletResponse credit(
            PlayerWalletEntity wallet, String rewardType, long amount) {
        switch (rewardType) {
            case "COIN" -> wallet.addCoins(amount);
            case "DIAMOND" -> wallet.addDiamonds(amount);
            case "ROOM_CARD" -> wallet.addRoomCards(amount);
            default ->
                    throw new IllegalStateException(
                            "Unsupported time login reward type: " + rewardType);
        }
        return ShopWalletResponse.from(repository.save(wallet));
    }
}
