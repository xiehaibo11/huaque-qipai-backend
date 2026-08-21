package com.nanbei.entertainment.backend.shop.infrastructure;

import com.nanbei.entertainment.backend.shop.domain.ShopInventoryItemEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShopInventoryItemRepository
        extends JpaRepository<ShopInventoryItemEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from ShopInventoryItemEntity item where item.userId = :userId and item.itemCode = :itemCode")
    Optional<ShopInventoryItemEntity> findLocked(
            @Param("userId") UUID userId, @Param("itemCode") String itemCode);

    List<ShopInventoryItemEntity> findByUserIdOrderByItemCodeAsc(UUID userId);
}
