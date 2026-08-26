package com.nanbei.entertainment.backend.wechatsubscription.infrastructure;

import com.nanbei.entertainment.backend.wechatsubscription.domain.WeChatSubscriptionGrantEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeChatSubscriptionGrantRepository
        extends JpaRepository<WeChatSubscriptionGrantEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select grant from WeChatSubscriptionGrantEntity grant where grant.id = :id")
    Optional<WeChatSubscriptionGrantEntity> findLockedById(@Param("id") UUID id);

    @Query(
            value =
                    """
      SELECT * FROM wechat_subscription_grants g
      WHERE g.user_id = :userId
        AND g.template_id = :templateId
        AND g.scene = :scene
        AND g.status = 'AVAILABLE'
      ORDER BY g.confirmed_at, g.id
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                    """,
            nativeQuery = true)
    Optional<WeChatSubscriptionGrantEntity> findOldestAvailableLocked(
            @Param("userId") UUID userId,
            @Param("templateId") String templateId,
            @Param("scene") int scene);

    @Modifying
    @Query(
            value =
                    """
                    UPDATE wechat_subscription_grants
                    SET status = 'INVALIDATED', updated_at = :occurredAt,
                        version = version + 1
                    WHERE user_id = :userId
                      AND status IN ('PENDING', 'AVAILABLE', 'CLAIMED')
                    """,
            nativeQuery = true)
    int invalidateUnused(
            @Param("userId") UUID userId,
            @Param("occurredAt") java.time.Instant occurredAt);
}
