package com.nanbei.entertainment.backend.shop.application;

public record ShopPurchaseResponse(
        String purchaseId,
        String productCode,
        String status,
        boolean duplicate,
        ShopWalletResponse wallet) {}
