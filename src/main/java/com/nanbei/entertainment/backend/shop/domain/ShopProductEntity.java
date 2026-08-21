package com.nanbei.entertainment.backend.shop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shop_products")
public class ShopProductEntity {
    @Id private UUID id;

    @Column(name = "product_code", nullable = false, unique = true)
    private String productCode;

    @Column(nullable = false)
    private String category;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "icon_key", nullable = false)
    private String iconKey;

    @Column(name = "price_currency", nullable = false)
    private String priceCurrency;

    @Column(name = "price_amount", nullable = false)
    private long priceAmount;

    @Column(name = "reward_type", nullable = false)
    private String rewardType;

    @Column(name = "reward_quantity", nullable = false)
    private long rewardQuantity;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "daily_limit")
    private Integer dailyLimit;

    @Column(name = "lifetime_limit")
    private Integer lifetimeLimit;

    @Column(name = "payment_product_id")
    private UUID paymentProductId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ShopProductEntity() {}

    private ShopProductEntity(
            String productCode,
            String category,
            String displayName,
            String iconKey,
            String priceCurrency,
            long priceAmount,
            String rewardType,
            long rewardQuantity,
            int sortOrder,
            Integer dailyLimit,
            Integer lifetimeLimit,
            UUID paymentProductId) {
        if (productCode == null || productCode.isBlank() || priceAmount < 0 || rewardQuantity <= 0) {
            throw new IllegalArgumentException("invalid shop product");
        }
        this.id = UUID.randomUUID();
        this.productCode = productCode;
        this.category = category;
        this.displayName = displayName;
        this.iconKey = iconKey;
        this.priceCurrency = priceCurrency;
        this.priceAmount = priceAmount;
        this.rewardType = rewardType;
        this.rewardQuantity = rewardQuantity;
        this.sortOrder = sortOrder;
        this.enabled = true;
        this.dailyLimit = dailyLimit;
        this.lifetimeLimit = lifetimeLimit;
        this.paymentProductId = paymentProductId;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public static ShopProductEntity exchange(
            String productCode,
            String category,
            String displayName,
            String iconKey,
            String priceCurrency,
            long priceAmount,
            String rewardType,
            long rewardQuantity,
            int sortOrder) {
        return new ShopProductEntity(
                productCode,
                category,
                displayName,
                iconKey,
                priceCurrency,
                priceAmount,
                rewardType,
                rewardQuantity,
                sortOrder,
                null,
                null,
                null);
    }

    public static ShopProductEntity paid(
            String productCode,
            String category,
            String displayName,
            String iconKey,
            long priceAmount,
            String rewardType,
            long rewardQuantity,
            int sortOrder,
            UUID paymentProductId) {
        return new ShopProductEntity(
                productCode,
                category,
                displayName,
                iconKey,
                "CNY",
                priceAmount,
                rewardType,
                rewardQuantity,
                sortOrder,
                null,
                null,
                paymentProductId);
    }

    public static ShopProductEntity paid(
            String productCode,
            String category,
            String displayName,
            String iconKey,
            long priceAmount,
            String rewardType,
            long rewardQuantity,
            int sortOrder,
            Integer dailyLimit,
            Integer lifetimeLimit,
            UUID paymentProductId) {
        return new ShopProductEntity(
                productCode,
                category,
                displayName,
                iconKey,
                "CNY",
                priceAmount,
                rewardType,
                rewardQuantity,
                sortOrder,
                dailyLimit,
                lifetimeLimit,
                paymentProductId);
    }

    public UUID getId() { return id; }
    public String getProductCode() { return productCode; }
    public String getCategory() { return category; }
    public String getDisplayName() { return displayName; }
    public String getIconKey() { return iconKey; }
    public String getPriceCurrency() { return priceCurrency; }
    public long getPriceAmount() { return priceAmount; }
    public String getRewardType() { return rewardType; }
    public long getRewardQuantity() { return rewardQuantity; }
    public int getSortOrder() { return sortOrder; }
    public boolean isEnabled() { return enabled; }
    public Integer getDailyLimit() { return dailyLimit; }
    public Integer getLifetimeLimit() { return lifetimeLimit; }
    public UUID getPaymentProductId() { return paymentProductId; }
}
