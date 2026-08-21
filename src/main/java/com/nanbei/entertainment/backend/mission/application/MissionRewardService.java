package com.nanbei.entertainment.backend.mission.application;

import com.nanbei.entertainment.backend.gamehome.application.PlayerProfileService;
import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.mission.domain.MissionRewardEntity;
import com.nanbei.entertainment.backend.shop.domain.ShopInventoryItemEntity;
import com.nanbei.entertainment.backend.shop.infrastructure.ShopInventoryItemRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MissionRewardService {
    private final PlayerProfileService profileService;
    private final PlayerWalletRepository walletRepository;
    private final ShopInventoryItemRepository inventoryRepository;

    public MissionRewardService(
            PlayerProfileService profileService,
            PlayerWalletRepository walletRepository,
            ShopInventoryItemRepository inventoryRepository) {
        this.profileService = profileService;
        this.walletRepository = walletRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional
    public PlayerWalletEntity grant(UUID userId, List<MissionRewardEntity> rewards) {
        profileService.ensureProfile(userId);
        PlayerWalletEntity wallet = walletRepository.findLockedByUserId(userId)
                .orElseGet(() -> new PlayerWalletEntity(userId, 0, 0, 0, 0));
        for (MissionRewardEntity reward : rewards) {
            switch (reward.getRewardType()) {
                case COIN -> wallet.addCoins(reward.getAmount());
                case DIAMOND -> wallet.addDiamonds(reward.getAmount());
                case ROOM_CARD -> wallet.addRoomCards(reward.getAmount());
                case COUPON -> wallet.addCoupons(reward.getAmount());
                case INVENTORY -> grantInventory(userId, reward);
            }
        }
        return walletRepository.save(wallet);
    }

    private void grantInventory(UUID userId, MissionRewardEntity reward) {
        var existing = inventoryRepository.findLocked(userId, reward.getItemCode());
        ShopInventoryItemEntity item;
        if (existing.isPresent()) {
            item = existing.orElseThrow();
            item.addQuantity(reward.getAmount());
        } else {
            item = new ShopInventoryItemEntity(
                    userId, reward.getItemCode(), reward.getAmount());
        }
        inventoryRepository.save(item);
    }
}
