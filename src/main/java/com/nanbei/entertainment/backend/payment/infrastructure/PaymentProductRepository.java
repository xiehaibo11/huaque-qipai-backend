package com.nanbei.entertainment.backend.payment.infrastructure;

import com.nanbei.entertainment.backend.payment.domain.PaymentProductEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentProductRepository
        extends JpaRepository<PaymentProductEntity, UUID> {
    List<PaymentProductEntity> findByEnabledTrueOrderByAmountMinorAsc();

    Optional<PaymentProductEntity> findByProductCodeAndEnabledTrue(
            String productCode);
}
