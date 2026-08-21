package com.nanbei.entertainment.backend.shop.infrastructure;

import com.nanbei.entertainment.backend.shop.domain.ShopPurchaseRecordEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopPurchaseRecordRepository
        extends JpaRepository<ShopPurchaseRecordEntity, UUID> {
    Optional<ShopPurchaseRecordEntity> findByUserIdAndIdempotencyKey(
            UUID userId, String idempotencyKey);
    boolean existsByOrderId(UUID orderId);
    long countByUserIdAndProductIdAndCreatedAtGreaterThanEqual(
            UUID userId, UUID productId, Instant since);
    long countByUserIdAndProductId(UUID userId, UUID productId);
}
