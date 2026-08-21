package com.nanbei.entertainment.backend.shop.application;

import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;

public record ShopWalletResponse(long roomCards, long coins, long diamonds, long coupons) {
    public static ShopWalletResponse from(PlayerWalletEntity wallet) {
        return new ShopWalletResponse(
                wallet.getRoomCards(),
                wallet.getCoins(),
                wallet.getDiamonds(),
                wallet.getCoupons());
    }
}
