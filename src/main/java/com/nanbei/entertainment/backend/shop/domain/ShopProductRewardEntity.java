package com.nanbei.entertainment.backend.shop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "shop_product_rewards")
public class ShopProductRewardEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "reward_type", nullable = false)
    private String rewardType;

    @Column(name = "reward_quantity", nullable = false)
    private long rewardQuantity;

    @Column(name = "item_code")
    private String itemCode;

    @Column(name = "grant_order", nullable = false)
    private int grantOrder;

    @Column(name = "purchase_number", nullable = false)
    private int purchaseNumber;

    protected ShopProductRewardEntity() {}

    public static ShopProductRewardEntity reward(
            UUID productId,
            String rewardType,
            long rewardQuantity,
            String itemCode,
            int grantOrder,
            int purchaseNumber) {
        ShopProductRewardEntity reward = new ShopProductRewardEntity();
        reward.productId = productId;
        reward.rewardType = rewardType;
        reward.rewardQuantity = rewardQuantity;
        reward.itemCode = itemCode;
        reward.grantOrder = grantOrder;
        reward.purchaseNumber = purchaseNumber;
        return reward;
    }

    public String getRewardType() {
        return rewardType;
    }

    public long getRewardQuantity() {
        return rewardQuantity;
    }

    public String getItemCode() {
        return itemCode;
    }
}
