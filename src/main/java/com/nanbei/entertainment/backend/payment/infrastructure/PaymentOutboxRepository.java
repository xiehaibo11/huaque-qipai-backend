package com.nanbei.entertainment.backend.payment.infrastructure;

import com.nanbei.entertainment.backend.payment.domain.PaymentOutboxEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentOutboxRepository
        extends JpaRepository<PaymentOutboxEntity, UUID> {
    boolean existsByEventTypeAndAggregateId(String eventType, UUID aggregateId);
}
