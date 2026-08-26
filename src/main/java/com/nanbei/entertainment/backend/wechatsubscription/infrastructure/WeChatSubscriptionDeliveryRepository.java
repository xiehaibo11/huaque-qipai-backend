package com.nanbei.entertainment.backend.wechatsubscription.infrastructure;

import com.nanbei.entertainment.backend.wechatsubscription.domain.WeChatSubscriptionDeliveryEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeChatSubscriptionDeliveryRepository
        extends JpaRepository<WeChatSubscriptionDeliveryEntity, UUID> {
    Optional<WeChatSubscriptionDeliveryEntity>
            findByUserIdAndTemplateIdAndEventTypeAndEventId(
                    UUID userId,
                    String templateId,
                    String eventType,
                    String eventId);

    @Query(
            value =
                    "SELECT pg_advisory_xact_lock("
                            + "hashtextextended(CAST(:lockKey AS text), 0))",
            nativeQuery = true)
    void acquireEventLock(@Param("lockKey") String lockKey);

    @Query(
            value =
                    """
                    SELECT * FROM wechat_subscription_deliveries delivery
                    WHERE delivery.status = 'PENDING'
                       OR (delivery.status = 'RETRYABLE'
                           AND delivery.next_attempt_at <= :now)
                    ORDER BY delivery.created_at, delivery.id
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                    """,
            nativeQuery = true)
    Optional<WeChatSubscriptionDeliveryEntity> findNextLocked(
            @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select delivery from WeChatSubscriptionDeliveryEntity delivery"
                    + " where delivery.id = :id")
    Optional<WeChatSubscriptionDeliveryEntity> findLockedById(
            @Param("id") UUID id);
}
