package com.nanbei.entertainment.backend.shop.infrastructure;

import com.nanbei.entertainment.backend.shop.domain.ShopProductRewardEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShopProductRewardRepository
        extends JpaRepository<ShopProductRewardEntity, Long> {
    @Query(
            """
            select reward
            from ShopProductRewardEntity reward
            where reward.productId = :productId
              and (reward.purchaseNumber = 0 or reward.purchaseNumber = :purchaseNumber)
            order by reward.grantOrder asc
            """)
    List<ShopProductRewardEntity> findForPurchase(
            @Param("productId") UUID productId,
            @Param("purchaseNumber") int purchaseNumber);
}
