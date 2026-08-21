package com.nanbei.entertainment.backend.shop.application;

import java.util.List;

public record ShopCatalogResponse(
        ShopWalletResponse wallet, List<ShopProductResponse> products) {}
