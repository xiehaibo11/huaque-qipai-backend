package com.nanbei.entertainment.backend.payment.domain;

import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_orders")
public class PaymentOrderEntity {
    @Id
    private UUID id;

    @Column(name = "merchant_order_no", nullable = false, unique = true)
    private String merchantOrderNo;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProviderType provider;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentOrderStatus status;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "provider_order_no")
    private String providerOrderNo;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected PaymentOrderEntity() {}

    public PaymentOrderEntity(
            UUID userId,
            PaymentProductEntity product,
            PaymentProviderType provider,
            String idempotencyKey) {
        this.id = UUID.randomUUID();
        this.merchantOrderNo =
                "NB"
                        + UUID.randomUUID()
                                .toString()
                                .replace("-", "")
                                .substring(0, 30);
        this.userId = userId;
        this.productId = product.getId();
        this.provider = provider;
        this.amountMinor = product.getAmountMinor();
        this.currency = product.getCurrency();
        this.status = PaymentOrderStatus.CREATED;
        this.idempotencyKey = idempotencyKey;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void markPending(String providerOrderNo) {
        requireState(PaymentOrderStatus.CREATED);
        this.providerOrderNo = providerOrderNo;
        status = PaymentOrderStatus.PENDING;
    }

    public boolean markPaid(String callbackProviderOrderNo, Instant paidAt) {
        if (status == PaymentOrderStatus.PAID) {
            return false;
        }
        if (status != PaymentOrderStatus.PENDING
                && status != PaymentOrderStatus.CREATED) {
            throw new ApiException(
                    ErrorCode.PAYMENT_ILLEGAL_STATE,
                    "订单当前状态不能变更为 PAID");
        }
        if (providerOrderNo != null
                && !providerOrderNo.equals(callbackProviderOrderNo)) {
            throw new ApiException(
                    ErrorCode.PAYMENT_CALLBACK_INVALID,
                    "渠道订单号不匹配");
        }
        providerOrderNo = callbackProviderOrderNo;
        status = PaymentOrderStatus.PAID;
        this.paidAt = paidAt;
        return true;
    }

    public void markFailed(String failureCode) {
        requireState(PaymentOrderStatus.CREATED);
        status = PaymentOrderStatus.FAILED;
        this.failureCode = failureCode;
    }

    private void requireState(PaymentOrderStatus expected) {
        if (status != expected) {
            throw new ApiException(
                    ErrorCode.PAYMENT_ILLEGAL_STATE,
                    "订单状态应为 " + expected + "，实际为 " + status);
        }
    }

    public UUID getId() {
        return id;
    }

    public String getMerchantOrderNo() {
        return merchantOrderNo;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getProductId() {
        return productId;
    }

    public PaymentProviderType getProvider() {
        return provider;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentOrderStatus getStatus() {
        return status;
    }

    public String getProviderOrderNo() {
        return providerOrderNo;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
