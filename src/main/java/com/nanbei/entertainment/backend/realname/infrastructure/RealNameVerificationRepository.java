package com.nanbei.entertainment.backend.realname.infrastructure;

import com.nanbei.entertainment.backend.realname.domain.RealNameVerificationEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RealNameVerificationRepository
        extends JpaRepository<RealNameVerificationEntity, UUID> {
    Optional<RealNameVerificationEntity> findByIdCardHmac(String idCardHmac);
}
