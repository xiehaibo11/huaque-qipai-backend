package com.nanbei.entertainment.backend.shop.application;

import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.shop.infrastructure.ShopProductRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShopCatalogService {
    private final ShopProductRepository productRepository;
    private final PlayerWalletRepository walletRepository;

    public ShopCatalogService(
            ShopProductRepository productRepository,
            PlayerWalletRepository walletRepository) {
        this.productRepository = productRepository;
        this.walletRepository = walletRepository;
    }

    @Transactional(readOnly = true)
    public ShopCatalogResponse load(UUID userId) {
        PlayerWalletEntity wallet =
                walletRepository
                        .findById(userId)
                        .orElseGet(() -> new PlayerWalletEntity(userId, 0, 0, 0, 0));
        return new ShopCatalogResponse(
                ShopWalletResponse.from(wallet),
                productRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
                        .map(ShopProductResponse::from)
                        .toList());
    }
}
