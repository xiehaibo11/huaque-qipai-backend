package com.nanbei.entertainment.backend.shop.application;

import com.nanbei.entertainment.backend.shop.domain.ShopInventoryItemEntity;

public record ShopInventoryResponse(String itemCode, long quantity) {
    static ShopInventoryResponse from(ShopInventoryItemEntity item) {
        return new ShopInventoryResponse(item.getItemCode(), item.getQuantity());
    }
}
