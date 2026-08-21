package com.nanbei.entertainment.backend.membership.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "membership_products")
public class MembershipProductEntity {
    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "product_code", nullable = false, unique = true)
    private String productCode;

    @Column(name = "plan_code", nullable = false, unique = true)
    private String planCode;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(name = "gift_value_yuan", nullable = false)
    private int giftValueYuan;

    @Column(name = "price_text", nullable = false)
    private String priceText;

    @Column(name = "day_cost_text", nullable = false)
    private String dayCostText;

    @Column(name = "card_style", nullable = false)
    private String cardStyle;

    @Column(name = "corner_tag", nullable = false)
    private String cornerTag;

    @Column(nullable = false)
    private boolean subscription;

    @Column(name = "privileges_count", nullable = false)
    private int privilegesCount;

    @Column(name = "daily_gift_value_yuan", nullable = false)
    private int dailyGiftValueYuan;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String rewards;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MembershipProductEntity() {}

    public UUID getProductId() {
        return productId;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getPlanCode() {
        return planCode;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public int getGiftValueYuan() {
        return giftValueYuan;
    }

    public String getPriceText() {
        return priceText;
    }

    public String getDayCostText() {
        return dayCostText;
    }

    public String getCardStyle() {
        return cardStyle;
    }

    public String getCornerTag() {
        return cornerTag;
    }

    public boolean isSubscription() {
        return subscription;
    }

    public int getPrivilegesCount() {
        return privilegesCount;
    }

    public int getDailyGiftValueYuan() {
        return dailyGiftValueYuan;
    }

    public String getRewards() {
        return rewards;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }
}
