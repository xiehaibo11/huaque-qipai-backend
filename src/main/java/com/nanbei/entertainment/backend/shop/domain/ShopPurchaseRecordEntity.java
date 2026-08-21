package com.nanbei.entertainment.backend.shop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shop_purchase_records")
public class ShopPurchaseRecordEntity {
    @Id private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "price_currency", nullable = false)
    private String priceCurrency;

    @Column(name = "price_amount", nullable = false)
    private long priceAmount;

    @Column(name = "reward_type", nullable = false)
    private String rewardType;

    @Column(name = "reward_quantity", nullable = false)
    private long rewardQuantity;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ShopPurchaseRecordEntity() {}

    public ShopPurchaseRecordEntity(
            UUID userId,
            ShopProductEntity product,
            UUID orderId,
            String idempotencyKey) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.productId = product.getId();
        this.productCode = product.getProductCode();
        this.orderId = orderId;
        this.idempotencyKey = idempotencyKey;
        this.priceCurrency = product.getPriceCurrency();
        this.priceAmount = product.getPriceAmount();
        this.rewardType = product.getRewardType();
        this.rewardQuantity = product.getRewardQuantity();
        this.status = "FULFILLED";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getProductId() { return productId; }
    public String getProductCode() { return productCode; }
    public UUID getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
