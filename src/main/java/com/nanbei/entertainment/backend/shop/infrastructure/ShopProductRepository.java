package com.nanbei.entertainment.backend.shop.infrastructure;

import com.nanbei.entertainment.backend.shop.domain.ShopProductEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopProductRepository extends JpaRepository<ShopProductEntity, UUID> {
    List<ShopProductEntity> findByEnabledTrueOrderBySortOrderAsc();
    Optional<ShopProductEntity> findByProductCodeAndEnabledTrue(String productCode);
    Optional<ShopProductEntity> findByPaymentProductIdAndEnabledTrue(UUID paymentProductId);
}
