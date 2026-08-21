package com.nanbei.entertainment.backend.payment.infrastructure;

import com.nanbei.entertainment.backend.payment.domain.PaymentOrderEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentOrderRepository
        extends JpaRepository<PaymentOrderEntity, UUID> {
    Optional<PaymentOrderEntity> findByUserIdAndIdempotencyKey(
            UUID userId, String idempotencyKey);

    @Query(
            value =
                    "SELECT pg_advisory_xact_lock("
                            + "hashtextextended(CAST(:lockKey AS text), 0))",
            nativeQuery = true)
    void acquireIdempotencyLock(@Param("lockKey") String lockKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select paymentOrder from PaymentOrderEntity paymentOrder "
                    + "where paymentOrder.merchantOrderNo = :merchantOrderNo")
    Optional<PaymentOrderEntity> findLockedByMerchantOrderNo(
            @Param("merchantOrderNo") String merchantOrderNo);

    @Query(
            value =
                    """
                    SELECT COUNT(*)
                    FROM payment_orders
                    WHERE user_id = :userId
                      AND product_id = :productId
                      AND status IN ('CREATED', 'PENDING', 'PAID')
                      AND created_at >= :since
                    """,
            nativeQuery = true)
    long countActiveOrdersSince(
            @Param("userId") UUID userId,
            @Param("productId") UUID productId,
            @Param("since") Instant since);

    @Query(
            value =
                    """
                    SELECT COUNT(*)
                    FROM payment_orders
                    WHERE user_id = :userId
                      AND product_id = :productId
                      AND status IN ('CREATED', 'PENDING', 'PAID')
                    """,
            nativeQuery = true)
    long countActiveOrders(
            @Param("userId") UUID userId,
            @Param("productId") UUID productId);
}
