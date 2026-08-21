package com.nanbei.entertainment.backend.shop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shop_inventory_items")
public class ShopInventoryItemEntity {
    @Id private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ShopInventoryItemEntity() {}

    public ShopInventoryItemEntity(UUID userId, String itemCode, long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("inventory quantity must be positive");
        }
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.itemCode = itemCode;
        this.quantity = quantity;
        this.updatedAt = Instant.now();
    }

    public void addQuantity(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("inventory quantity must be positive");
        }
        quantity = Math.addExact(quantity, amount);
        updatedAt = Instant.now();
    }

    public String getItemCode() { return itemCode; }
    public long getQuantity() { return quantity; }
}
