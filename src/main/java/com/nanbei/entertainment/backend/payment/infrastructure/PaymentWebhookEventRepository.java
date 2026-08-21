package com.nanbei.entertainment.backend.payment.infrastructure;

import com.nanbei.entertainment.backend.payment.domain.PaymentProviderType;
import com.nanbei.entertainment.backend.payment.domain.PaymentWebhookEventEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentWebhookEventRepository
        extends JpaRepository<PaymentWebhookEventEntity, UUID> {
    @Query(
            value =
                    "SELECT pg_advisory_xact_lock("
                            + "hashtextextended(CAST(:lockKey AS text), 0))",
            nativeQuery = true)
    void acquireEventLock(@Param("lockKey") String lockKey);

    Optional<PaymentWebhookEventEntity> findByProviderAndProviderEventId(
            PaymentProviderType provider, String providerEventId);
}
