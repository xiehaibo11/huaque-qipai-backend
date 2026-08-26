package com.nanbei.entertainment.backend.shop.application;

import com.nanbei.entertainment.backend.gamehome.domain.PlayerWalletEntity;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.shop.domain.ShopProductEntity;
import com.nanbei.entertainment.backend.shop.infrastructure.ShopProductRepository;
import com.nanbei.entertainment.backend.shop.infrastructure.ShopPurchaseRecordRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShopCatalogService {
    private static final ZoneId CHINA_TIME = ZoneId.of("Asia/Shanghai");

    private final ShopProductRepository productRepository;
    private final ShopPurchaseRecordRepository purchaseRepository;
    private final PlayerWalletRepository walletRepository;

    public ShopCatalogService(
            ShopProductRepository productRepository,
            ShopPurchaseRecordRepository purchaseRepository,
            PlayerWalletRepository walletRepository) {
        this.productRepository = productRepository;
        this.purchaseRepository = purchaseRepository;
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
                        .map(product -> response(userId, product))
                        .toList());
    }

    private ShopProductResponse response(UUID userId, ShopProductEntity product) {
        long purchasedLifetime = 0;
        long purchasedToday = 0;
        if (product.getLifetimeLimit() != null || product.getDailyLimit() != null) {
            purchasedLifetime =
                    purchaseRepository.countByUserIdAndProductId(userId, product.getId());
        }
        if (product.getDailyLimit() != null) {
            purchasedToday =
                    purchaseRepository.countByUserIdAndProductIdAndCreatedAtGreaterThanEqual(
                            userId,
                            product.getId(),
                            LocalDate.now(CHINA_TIME).atStartOfDay(CHINA_TIME).toInstant());
        }
        return ShopProductResponse.from(product, purchasedToday, purchasedLifetime);
    }
}
